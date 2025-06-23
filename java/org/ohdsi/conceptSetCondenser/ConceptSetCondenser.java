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
	private int optimalLength;
	private int firstExclusionIndex;
	private List<Set<Integer>> remainingConceptsAtLevel;

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
		// Sort candidate concepts to determine order in which tree is traversed.
		// First sorting so inclusions are executed first. This allows us to build the concept set 
		// on the fly (exclusions must always be done last).
		// Then sorting so inclusions that are not optional are done first. This just means they are 
		// not re-evaluated all the time
		// Finally sorting by the size of descendants. Since IGNORE is the first option
		// that is evaluated, this means in the first part of the tree search we will evaluate
		// options where these lower-level concepts are ignored.
		Comparator<CandidateConcept> comparator = new Comparator<CandidateConcept>() {
			public int compare(CandidateConcept obj1, CandidateConcept obj2) {
				int result = Boolean.compare(obj2.inConceptSet, obj1.inConceptSet);
				if (result != 0)
					return result;
				result = Boolean.compare(obj2.validOptions.length == 1, obj1.validOptions.length == 1);
				if (result != 0)
					return result;
				return Integer.compare(obj2.descendants.size(), obj1.descendants.size());
			}
		};
		candidateConcepts.sort(comparator);

		// At each level in the tree, determine what concepts could still be included or excluded from 
		// that point forward. This will help terminate a branch when it cannot achieve a valid solution:
		firstExclusionIndex = candidateConcepts.size();
		remainingConceptsAtLevel = new ArrayList<Set<Integer>>(candidateConcepts.size());
		Set<Integer> remainingConcepts = new HashSet<Integer>();
		for (int i = candidateConcepts.size() - 1; i >= 0; i--) {
			CandidateConcept candidateConcept = candidateConcepts.get(i);
			if (candidateConcept.inConceptSet) {
				if (i == firstExclusionIndex - 1)
					remainingConcepts.clear();
				if (candidateConcept.hasValidOption(CandidateConcept.Options.INCLUDE_WITH_DESCENDANTS)) {
					remainingConcepts.addAll(candidateConcept.descendants);
				} else {
					remainingConcepts.add(candidateConcept.conceptId);
				}
			} else {
				firstExclusionIndex = i;
				if (candidateConcept.hasValidOption(CandidateConcept.Options.EXCLUDE_WITH_DESCENDANTS)) {
					remainingConcepts.addAll(candidateConcept.descendants);
				} else {
					remainingConcepts.add(candidateConcept.conceptId);
				}
			}
			remainingConceptsAtLevel.add(new HashSet<Integer>(remainingConcepts));
		}
		Collections.reverse(remainingConceptsAtLevel);

		// For debugging: output candidateConcepts:
//		for (CandidateConcept candidateConcept : candidateConcepts) {
//			System.out.println("- Concept ID " + candidateConcept.conceptId + ", valid options:" + Arrays.toString(candidateConcept.validOptions));
//		}

		optimalLength = candidateConcepts.size() + 1;
		optimalSolution = new CandidateConcept.Options[candidateConcepts.size()];
		currentSolution = new CandidateConcept.Options[candidateConcepts.size()];
		recurseOverOptions(0, 0, new HashSet<Integer>());
	}

	private void recurseOverOptions(int index, int currentLength, Set<Integer> currentConceptSet) {
		if (currentLength > optimalLength){
			// Already cannot improve on current best solution
			return;
		} else if (index == candidateConcepts.size()) {
			evaluateCurrentSolution(currentLength, currentConceptSet);
		} else if (index == firstExclusionIndex && !currentConceptSet.containsAll(conceptSet)) {
			// Only exclusion options from here on, so if not all required concepts are in current
			// set there is no way to add them
			return;
		} else {
			if (index < firstExclusionIndex) {
				Set<Integer> missingConcepts = new HashSet<Integer>(conceptSet);
				missingConcepts.removeAll(currentConceptSet);
				if (!remainingConceptsAtLevel.get(index).containsAll(missingConcepts))
					return;
			} else {
				Set<Integer> surplusConcepts = new HashSet<Integer>(currentConceptSet);
				surplusConcepts.removeAll(conceptSet);
				if (!remainingConceptsAtLevel.get(index).containsAll(surplusConcepts))
					return;
			}
			
			for (CandidateConcept.Options option : candidateConcepts.get(index).validOptions) {
				currentSolution[index] = option;
				Set<Integer> newConceptSet;
				int newLength = currentLength;
				switch (option) {
				case INCLUDE:
					if (currentConceptSet.contains(candidateConcepts.get(index).conceptId))
						continue;
					newConceptSet = new HashSet<Integer>(currentConceptSet);
					newConceptSet.add(candidateConcepts.get(index).conceptId);
					newLength++;
					break;
				case INCLUDE_WITH_DESCENDANTS:
					if (currentConceptSet.containsAll(candidateConcepts.get(index).descendants))
						continue;
					newConceptSet = new HashSet<Integer>(currentConceptSet);
					newConceptSet.addAll(candidateConcepts.get(index).descendants);
					newLength++;
					break;
				case EXCLUDE:
					if (!currentConceptSet.contains(candidateConcepts.get(index).conceptId))
						continue;
					newConceptSet = new HashSet<Integer>(currentConceptSet);
					newConceptSet.remove(candidateConcepts.get(index).conceptId);
					newLength++;
					break;
				case EXCLUDE_WITH_DESCENDANTS:
					if (Collections.disjoint(currentConceptSet, candidateConcepts.get(index).descendants))
						continue;
					newConceptSet = new HashSet<Integer>(currentConceptSet);
					newConceptSet.removeAll(candidateConcepts.get(index).descendants);
					newLength++;
					break;
				default:
					// IGNORE
					newConceptSet = currentConceptSet;
					break;
				}
				recurseOverOptions(index + 1, newLength, newConceptSet);
			}
		}
	}

	private void evaluateCurrentSolution(int currentLength, Set<Integer> currentConceptSet) {
		if (currentConceptSet.equals(conceptSet)) {
			if (currentLength < optimalLength) {
				optimalSolution = Arrays.copyOf(currentSolution, currentSolution.length);
				optimalLength = currentLength;
			}
		}
	}

	private void determineValidStatesAndRemoveRedundant() {
		Set<Integer> candidatesToRemove = new HashSet<Integer>();
		List<CandidateConcept.Options> validOptions = new ArrayList<CandidateConcept.Options>(5);
		for (CandidateConcept candidateConcept : candidateConcepts) {
			if (candidatesToRemove.contains(candidateConcept.conceptId))
				continue;
			validOptions.clear();

			if (conceptSet.contains(candidateConcept.conceptId)) {
				// Concept is in the concept set
				candidateConcept.inConceptSet = true;
				if (candidateConcept.descendants.size() == 1) {
					// Has no descendants, so no difference between INCLUDE and
					// INCLUDE_WITH_DESCENDANTS
					validOptions.add(CandidateConcept.Options.INCLUDE_WITH_DESCENDANTS);
				} else {
					if (conceptSet.containsAll(candidateConcept.descendants)) {
						// All descendants are in concept set, so no reason to evaluate
						// INCLUDE
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
				// If concept is not a descendant of any other concept, it is not an option to
				// ignore it
				if (!cantIgnore(candidateConcept)) {
					validOptions.add(CandidateConcept.Options.IGNORE);
				}
			} else {
				// Concept is *not* in the concept set
				candidateConcept.inConceptSet = false;
				validOptions.add(CandidateConcept.Options.IGNORE);
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
