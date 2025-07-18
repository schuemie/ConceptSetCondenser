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

#' Fetch data for concept set condensation
#'
#' @param conceptSetExpression The concept set expression to condense. This can
#'                             be either a JSON expression or the output of 
#'                             `ROhdsiWebApi::getConceptSetDefinition`.
#' @param connectionDetails    The output of `DatabaseConnector::createConnectionDetails()`.
#'                             Is ignored if `connection` is provided.
#' @param connection           A `DatabaseConnector` connection. Can be NULL if 
#'                             `connectionDetails` is provided.
#' @param cdmDatabaseSchema    A database schema holding the OHDSI Vocabulary
#'                             tables.
#' @param tempEmulationSchema  For database platforms that do not natively
#'                             support temp tables, a database schema where the
#'                             user has write access.
#'            
#' @returns
#' A list containing the three inputs for `condenseConceptSet()`.
#' 
#' @export
fetchConceptSetData <- function(conceptSetExpression, 
                                connectionDetails = NULL,
                                connection = NULL,
                                cdmDatabaseSchema,
                                tempEmulationSchema = getOption("sqlRenderTempEmulationSchema")) {
  if (is.null(connection)) {
    connection <- DatabaseConnector::connect(connectionDetails)
    on.exit(DatabaseConnector::disconnect(connection))
  }
  DatabaseConnector::assertTempEmulationSchemaSet(
    dbms = DatabaseConnector::dbms(connection),
    tempEmulationSchema = tempEmulationSchema
  )
  if (!is.character(conceptSetExpression)) {
    if ("expression" %in% names(conceptSetExpression)) {
      conceptSetExpression <- conceptSetExpression$expression
    }
    conceptSetExpression <- jsonlite::toJSON(conceptSetExpression, auto_unbox  = TRUE)
  }
  message("Instantiating concept set")
  sql <- CirceR::buildConceptSetQuery(as.character(conceptSetExpression))
  sql <- sprintf("SELECT concept_id INTO #concept_set FROM (%s);", sql)
  DatabaseConnector::renderTranslateExecuteSql(
    connection = connection,
    sql = sql,
    vocabulary_database_schema = cdmDatabaseSchema,
    tempEmulationSchema = tempEmulationSchema
  )
  message("Fetching included concepts")
  includedConceptIds <- DatabaseConnector::renderTranslateQuerySql(
    connection = connection,
    sql = "SELECT concept_id FROM #concept_set;",
    snakeCaseToCamelCase = TRUE
  )$conceptId
  
  # Union with original concept set to also include non-standard concepts (which
  # do not have records in the concept_ancestor table).
  sql <- "
    SELECT DISTINCT concept_id
    INTO #universe
    FROM (
      SELECT DISTINCT descendant_concept_id AS concept_id
      FROM #concept_set
      INNER JOIN @cdm_database_schema.concept_ancestor
        ON concept_id = ancestor_concept_id
      
      UNION ALL
      
      SELECT concept_id
      FROM #concept_set
    ) tmp;
  "
  DatabaseConnector::renderTranslateExecuteSql(
    connection = connection,
    sql = sql,
    cdm_database_schema = cdmDatabaseSchema,
    progressBar = FALSE,
    reportOverallTime = FALSE
  )
  message("Fetching concept metadata")
  sql <- "
    SELECT concept.*
    FROM #universe universe
    INNER JOIN @cdm_database_schema.concept
      ON concept.concept_id = universe.concept_id;
  "
  conceptMetaData <- DatabaseConnector::renderTranslateQuerySql(
    connection = connection,
    sql = sql,
    cdm_database_schema = cdmDatabaseSchema
  )
  message("Fetching concept descendants")
  # Also creating descendant (is the same as ancestor concept) for non-standard 
  # concepts.
  sql <- "
    SELECT DISTINCT concept_id,
      descendant_concept_id
    FROM (
      SELECT concept_id,
        descendant_concept_id
      FROM #universe
      INNER JOIN @cdm_database_schema.concept_ancestor
        ON concept_id = ancestor_concept_id
        
      UNION ALL
      
      SELECT concept_id,
        concept_id AS descendant_concept_id
      FROM #universe
    ) tmp;
  "
  conceptsToDescendants <- DatabaseConnector::renderTranslateQuerySql(
    connection = connection,
    sql = sql,
    snakeCaseToCamelCase = TRUE,
    cdm_database_schema = cdmDatabaseSchema
  )
  sql <- "
    TRUNCATE TABLE #concept_set; 
    DROP TABLE #concept_set;
    TRUNCATE TABLE #universe; 
    DROP TABLE #universe;
  "
  DatabaseConnector::renderTranslateExecuteSql(
    connection = connection,
    sql = sql,
    progressBar = FALSE,
    reportOverallTime = FALSE
  )
  message(sprintf("Universe of concepts is %d concepts",  nrow(conceptMetaData)))
  return(list(includedConceptIds = includedConceptIds,
              conceptMetaData = conceptMetaData,
              conceptsToDescendants = conceptsToDescendants))
}
