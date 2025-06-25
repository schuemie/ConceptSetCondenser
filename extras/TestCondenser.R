library(ConceptSetCondenser)

# Settings ---------------------------------------------------------------------
# conceptSetId <- 9672 # 10989
newConceptSetName <- "[mschuemi] Test upload"

conceptSetExpression <- jsonlite::fromJSON("e:/temp/conceptSet.json")
conceptSetExpression <- SqlRender::readSql("e:/temp/conceptSet.json")

# Need a database connection to fetch vocabulary data:
connectionDetails <- DatabaseConnector::createConnectionDetails(
  dbms = "spark",
  connectionString = keyring::key_get("databricksConnectionString"),
  user = "token",
  password = keyring::key_get("databricksToken")
)
cdmDatabaseSchema <- "merative_mdcr.cdm_merative_mdcr_v3045"
options(sqlRenderTempEmulationSchema = "scratch.scratch_mschuemi")


# Get concept set expression from ATLAS ----------------------------------------
# ROhdsiWebApi::authorizeWebApi(Sys.getenv("baseUrl"), "windows")
# conceptSetExpression <- ROhdsiWebApi::getConceptSetDefinition(conceptSetId, Sys.getenv("baseUrl"))


# Fetch data -------------------------------------------------------------------
conceptSetData <- fetchConceptSetData(
  conceptSetExpression = conceptSetExpression, 
  connectionDetails = connectionDetails,
  cdmDatabaseSchema = cdmDatabaseSchema
)


# Main condenser function ------------------------------------------------------
condensedConceptSet <- condenseConceptSet(conceptSetData)


# Use CirceR print friendly to print -------------------------------------------
condensedConceptSetList <- list(id = 0, name = "Condensed", expression = condensedConceptSet)
json <- as.character(jsonlite::toJSON(list(condensedConceptSetList), auto_unbox = TRUE))
writeLines(CirceR::conceptSetListPrintFriendly(json))


# Post condensed concept set to ATLAS ------------------------------------------
ROhdsiWebApi::postConceptSetDefinition(
  name = newConceptSetName,
  conceptSetDefinition = condensedConceptSet,
  baseUrl = Sys.getenv("baseUrl")
)

# ROhdsiWebApi::deleteConceptSetDefinition(11021, Sys.getenv("baseUrl"))



# Joels code ---------------
conceptSetExpression <- jsonlite::read_json("e:/temp/conceptSet.json")

connectionDetails <- DatabaseConnector::createConnectionDetails(
  dbms = "spark",
  connectionString = keyring::key_get("databricksConnectionString"),
  user = "token",
  password = keyring::key_get("databricksToken")
)
cdmDatabaseSchema <- "merative_ccae.cdm_merative_ccae_v3467"
options(sqlRenderTempEmulationSchema = "scratch.scratch_mschuemi")
connection <- DatabaseConnector::connect(connectionDetails)

# Fetch data -------------------------------------------------------------------
conceptSetData <- ConceptSetCondenser::fetchConceptSetData(
  conceptSetExpression = conceptSetExpression,
  connection = connection,
  cdmDatabaseSchema = cdmDatabaseSchema
)


# Main condenser function ------------------------------------------------------
condensedConceptSet <- ConceptSetCondenser::condenseConceptSet(conceptSetData)

library(dplyr)
universe <- conceptSetData$conceptsToDescendants |>
  distinct(conceptId) |>
  pull()
which(!conceptSetData$includedConceptIds %in% universe)  

conceptSetData$includedConceptIds[1]
37204149 %in% conceptSetData$conceptsToDescendants$conceptId
37204149 %in% conceptSetData$conceptMetaData$conceptId

DatabaseConnector::renderTranslateQuerySql(
  connection, "SELECT * FROM @cdm.concept WHERE concept_id = @cid",
  cdm = cdmDatabaseSchema,
  cid = 37204149
)
