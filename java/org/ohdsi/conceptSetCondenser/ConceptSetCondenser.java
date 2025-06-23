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
import java.util.Comparator;
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
		// Sort candidate concepts by size of descendants. Since IGNORE is the first option
		// that is evaluated, this means in the first part of the tree search we will evaluate
		// options where these lower-level concepts are ignored.
		Comparator<CandidateConcept> comparator = new Comparator<CandidateConcept>() {
			public int compare(CandidateConcept obj1, CandidateConcept obj2) {
		        return Integer.compare(obj1.descendants.size(), obj2.descendants.size());
		    }
		};
		candidateConcepts.sort(comparator);
		
		// Baseline solution: include all concepts in concept set without descendants:
		optimalLength = 0;
		optimalSolution = new CandidateConcept.Options[candidateConcepts.size()];
		for (int i = 0; i < optimalSolution.length; i++) {
			CandidateConcept candidateConcept = candidateConcepts.get(i);
			if (conceptSet.contains(candidateConcept.conceptId)) {
				optimalSolution[i] = CandidateConcept.Options.INCLUDE;
				optimalLength++;
			} else 
				optimalSolution[i] = CandidateConcept.Options.IGNORE;
		}
		System.out.println("Full tree size is " + computeTreeSize() + " solutions");
		solutionsEvaluated = 0;
		currentSolution = new CandidateConcept.Options[candidateConcepts.size()];
		recurseOverOptions(0, 0);
		System.out.println("Evaluated " + solutionsEvaluated + " solutions");
		System.out.println("Optimal solution has " + optimalLength + " concepts");
	}
	
	private double computeTreeSize() {
		double treeSize = 1;
		for (CandidateConcept candidateConcept : candidateConcepts) {
			System.out.println("- Concept ID " + candidateConcept.conceptId + ", valid options:" + Arrays.toString(candidateConcept.validOptions));
			treeSize *= candidateConcept.validOptions.length;
		}
		return treeSize;
	}

	private void recurseOverOptions(int index, int notIgnored) {
		if (notIgnored > optimalLength){
			// Already cannot improve on current best solution
			return;
		} else if (index == candidateConcepts.size()) {
			evaluateCurrentSolution();
		} else {
			for (CandidateConcept.Options option : candidateConcepts.get(index).validOptions) {
				currentSolution[index] = option;
				if (option != CandidateConcept.Options.IGNORE)
					notIgnored++;
				recurseOverOptions(index + 1, notIgnored);
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
				System.out.println("Found new optimum with " + optimalLength + " concepts");
			}
		}
		solutionsEvaluated++;
		if (solutionsEvaluated % 1000000 == 0)
			System.out.println("Evaluated " + solutionsEvaluated + " solutions");
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
					validOptions.add(CandidateConcept.Options.EXCLUDE);
					if (Collections.disjoint(conceptSet, candidateConcept.descendants)) {
						// None of the descendants are in concept set, so can EXCLUDE_WITH_DESCENDANTS 
						validOptions.add(CandidateConcept.Options.EXCLUDE_WITH_DESCENDANTS);
						
						// Also, descendants are redundant, so can be removed from candidate list:
						Set<Integer> toRemove = new HashSet<Integer>(candidateConcept.descendants);
						toRemove.remove(candidateConcept.conceptId);
						candidatesToRemove.addAll(toRemove);
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
