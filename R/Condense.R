# Copyright 2025 Observational Health Data Sciences and Informatics
#
# This file is part of ConceptSetCondenser
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# library(dplyr)

#' Condense concept set
#'
#' @param includedConceptIds    The concept IDs of all concepts included in the concept set. This should be
#'                              the fully resolved concept set.
#' @param conceptsToDescendants For all concepts included in the concept set plus all of their descendants, 
#'                              list all the descendants. This should be a tibble with the structure described
#'                              in the details section.
#'                              
#' @details
#' `conceptsToDescendants` should have these two columns:
#' 
#' 1. `conceptId`: the ID of the ancestor concept. Repeat this for all descendants of this concept.
#' 2. `descendantConceptId`:  the ID of the descendant concept. 
#' 
#' Following the OHDSI conventions, the descendants should include the concept itself. #' 
#'
#' @returns
#' A tibble with a representation of the optimal concept set expression
#'
#' @examples
#' includedConceptIds <- c(1, 2, 3, 4)
#' conceptsToDescendants <- data.frame(
#'   conceptId = c(1, 1, 1, 1, 2, 2, 3, 4 ,5),
#'   descendantConceptId = c(1, 2, 3, 5, 2, 3, 3, 4, 5)
#' )
#' condenseConceptSet(includedConceptIds, conceptsToDescendants)
#' 
#' @export
condenseConceptSet <- function(includedConceptIds, conceptsToDescendants) {
  candidateConcepts <- conceptsToDescendants |>
    group_by(.data$conceptId) |>
    group_split()
  # group = candidateConcepts[[1]]
  createCandidateConcept <- function(group) {
    rJava::.jnew("org.ohdsi.conceptSetCondenser.CandidateConcept", 
                 as.integer(group$conceptId[1]), 
                 rJava::.jarray(as.integer(group$descendantConceptId))
    )
  }
  candidateConcepts <- rJava::.jarray(lapply(candidateConcepts, createCandidateConcept), 
                                      contents.class = "org.ohdsi.conceptSetCondenser.CandidateConcept")
  condenser <- rJava::.jnew("org.ohdsi.conceptSetCondenser.ConceptSetCondenser",
                            rJava::.jarray(as.integer(includedConceptIds)),
                            candidateConcepts)
  condenser$condense()
  solution <- condenser$getConceptSetExpression()
  conceptSetExpression <- lapply(solution, function(x) tibble(conceptId = x$conceptId, exclude = x$exclude, descendants = x$descendants))
  conceptSetExpression <- bind_rows(conceptSetExpression)
  return(conceptSetExpression)
}
