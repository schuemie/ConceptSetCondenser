/*******************************************************************************
 * Copyright 2025 Observational Health Data Sciences and Informatics
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.ohdsi.conceptSetCondenser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConceptSetCondenser {

	private Set<Integer> conceptSet;
	private List<CandidateConcept> candidateConcepts;
	private CandidateConcept.Options[] currentSolution;
	private CandidateConcept.Options[] optimalSolution;
	private int optimalLength = -1;
	private int solutionsEvaluated;

	/**
	 * Constructor
	 * 
	 * @param conceptSet        The set of concept IDs included in the concept set.
	 * @param candidateConcepts An array of candidate concepts. A candidate concept
	 *                          is either in the concept set, or is a descendant of
	 *                          a concept in the concept set.
	 */
	public ConceptSetCondenser(int[] conceptSet, CandidateConcept[] candidateConcepts) {
		this.conceptSet = new HashSet<Integer>(conceptSet.length);
		for (int concept : conceptSet)
			this.conceptSet.add(concept);
		this.candidateConcepts = new ArrayList<CandidateConcept>(candidateConcepts.length);
		for (CandidateConcept candidateConcept : candidateConcepts)
			this.candidateConcepts.add(candidateConcept);
				
	}

	/**
	 * Condense the concept set to the optimal (shortest) expression.
	 */
	public void condense() {
		determineValidStatesAndRemoveRedundant();
		bruteForeSearch();
	}
	
	/**
	 * Get the optimal concept set expression. Will throw an exception if condense() hasn't
	 * been called first.
	 * 
	 * @return An array of ConceptExpression
	 */
	public ConceptExpression[] getConceptSetExpression() {
		if (optimalLength == -1)
			throw new RuntimeException("Must run condense() first");
		ConceptExpression[] expression = new ConceptExpression[optimalLength];
		int cursor = 0;
		for (int i = 0; i < optimalSolution.length; i++) {
			int conceptId = candidateConcepts.get(i).conceptId;
			switch (optimalSolution[i]) {
			case INCLUDE:
				expression[cursor++] = new ConceptExpression(conceptId, false, false);
				break;
			case INCLUDE_WITH_DESCENDANTS:
				expression[cursor++] = new ConceptExpression(conceptId, false, true);
				break;
			case EXCLUDE:
				expression[cursor++] = new ConceptExpression(conceptId, true, false);
				break;
			case EXCLUDE_WITH_DESCENDANTS:
				expression[cursor++] = new ConceptExpression(conceptId, true, true);
				break;
			case IGNORE:
				break;
			}
		}
		return expression;
	}

	private void bruteForeSearch() {
		optimalLength = candidateConcepts.size();
		currentSolution = new CandidateConcept.Options[candidateConcepts.size()];
		solutionsEvaluated = 0;
		recurseOverOptions(0);
		System.out.println("Evaluated " + solutionsEvaluated + " solutions");
		System.out.println("Optimal solution has " + optimalLength + " concepts");
	}

	private void recurseOverOptions(int index) {
		if (index == candidateConcepts.size()) {
			evaluateCurrentSolution();
		} else {
			for (CandidateConcept.Options option : candidateConcepts.get(index).validOptions) {
				currentSolution[index] = option;
				recurseOverOptions(index + 1);
			}
		}
	}

	private void evaluateCurrentSolution() {
		Set<Integer> includedConcepts = new HashSet<Integer>();
		Set<Integer> excludedConcepts = new HashSet<Integer>();
		int solutionLength = 0;
		for (int i = 0; i < currentSolution.length; i++) {
			switch (currentSolution[i]) {
			case INCLUDE:
				includedConcepts.add(candidateConcepts.get(i).conceptId);
				solutionLength++;
				break;
			case INCLUDE_WITH_DESCENDANTS:
				includedConcepts.addAll(candidateConcepts.get(i).descendants);
				solutionLength++;
				break;
			case EXCLUDE:
				excludedConcepts.add(candidateConcepts.get(i).conceptId);
				solutionLength++;
				break;
			case EXCLUDE_WITH_DESCENDANTS:
				excludedConcepts.addAll(candidateConcepts.get(i).descendants);
				solutionLength++;
				break;
			case IGNORE:
				break;
			}
		}
		includedConcepts.removeAll(excludedConcepts);
		if (includedConcepts.equals(conceptSet)) {
			if (solutionLength < optimalLength) {
				optimalSolution = Arrays.copyOf(currentSolution, currentSolution.length);
				optimalLength = solutionLength;
			}
		}
		solutionsEvaluated++;
	}

	private void determineValidStatesAndRemoveRedundant() {
		Set<Integer> candidatesToRemove = new HashSet<Integer>();
		List<CandidateConcept.Options> validOptions = new ArrayList<CandidateConcept.Options>(5);
		for (CandidateConcept candidateConcept : candidateConcepts) {
			if (candidatesToRemove.contains(candidateConcept.conceptId))
				continue;
			validOptions.clear();

			// If concept is not an ancestor of any other concept, it is not an option to
			// ignore it
			if (!cantIgnore(candidateConcept)) {
				validOptions.add(CandidateConcept.Options.IGNORE);
			}

			if (conceptSet.contains(candidateConcept.conceptId)) {
				// Concept is in the concept set
				if (candidateConcept.descendants.size() == 1) {
					// Has no descendants, so no difference between INCLUDE and
					// INCLUDE_WITH_DESCENDANTS
					validOptions.add(CandidateConcept.Options.INCLUDE_WITH_DESCENDANTS);
				} else {
					if (conceptSet.containsAll(candidateConcept.descendants)) {
						// All descendants are in concept set, so no reason to evaluate
						// INCLUDE_WITH_DESCENDANTS
						validOptions.add(CandidateConcept.Options.INCLUDE_WITH_DESCENDANTS);

						// Also, descendants are redundant, so can be removed from candidate list:
						Set<Integer> toRemove = new HashSet<Integer>(candidateConcept.descendants);
						toRemove.remove(candidateConcept.conceptId);
						candidatesToRemove.addAll(toRemove);
					} else {
						validOptions.add(CandidateConcept.Options.INCLUDE);
						validOptions.add(CandidateConcept.Options.INCLUDE_WITH_DESCENDANTS);
					}
				}
			} else {
				// Concept is *not* in the concept set
				if (candidateConcept.descendants.size() == 1) {
					// Has no descendants, so no difference between EXCLUDE and
					// EXCLUDE_WITH_DESCENDANTS
					validOptions.add(CandidateConcept.Options.EXCLUDE_WITH_DESCENDANTS);
				} else {
					if (Collections.disjoint(conceptSet, candidateConcept.descendants)) {
						// None of the descendants are in concept set, so no reason to evaluate
						// EXCLUDE_WITH_DESCENDANTS
						validOptions.add(CandidateConcept.Options.EXCLUDE_WITH_DESCENDANTS);

						// Also, descendants are redundant, so can be removed from candidate list:
						Set<Integer> toRemove = new HashSet<Integer>(candidateConcept.descendants);
						toRemove.remove(candidateConcept.conceptId);
						candidatesToRemove.addAll(toRemove);
					} else {
						validOptions.add(CandidateConcept.Options.EXCLUDE);
						validOptions.add(CandidateConcept.Options.EXCLUDE_WITH_DESCENDANTS);
					}
				}
			}
			candidateConcept.validOptions = validOptions.toArray(new CandidateConcept.Options[validOptions.size()]);
		}
		candidateConcepts.removeIf(candidateConcept -> candidatesToRemove.contains(candidateConcept.conceptId));
	}

	private boolean cantIgnore(CandidateConcept candidateConcept) {
		for (CandidateConcept otherCandidateConcept : candidateConcepts) {
			if (otherCandidateConcept != candidateConcept)
				if (otherCandidateConcept.descendants.contains(candidateConcept.conceptId))
					return false;
		}
		return true;
	}
}
