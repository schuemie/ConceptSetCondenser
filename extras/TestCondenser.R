library(ConceptSetCondenser)

connectionDetails <- DatabaseConnector::createConnectionDetails(
  dbms = "spark",
  connectionString = keyring::key_get("databricksConnectionString"),
  user = "token",
  password = keyring::key_get("databricksToken")
)
cdmDatabaseSchema <- "merative_mdcr.cdm_merative_mdcr_v3045"
options(sqlRenderTempEmulationSchema = "scratch.scratch_mschuemi")

conceptSetId <- 10989 # 11009

newConceptSetName <- "[mschuemi] Test upload"



# Get concept set expression from ATLAS ----------------------------------------
ROhdsiWebApi::authorizeWebApi(Sys.getenv("baseUrl"), "windows")
conceptSetExpression <- ROhdsiWebApi::getConceptSetDefinition(conceptSetId, Sys.getenv("baseUrl"))

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

# ROhdsiWebApi::deleteConceptSetDefinition(11019, Sys.getenv("baseUrl"))


