library(ConceptSetCondenser)

# Get example concept set expression -------------------------------------------
ROhdsiWebApi::authorizeWebApi(Sys.getenv("baseUrl"), "windows")
# conceptSetExpression <- ROhdsiWebApi::getConceptSetDefinition(11009, Sys.getenv("baseUrl"))
conceptSetExpression <- ROhdsiWebApi::getConceptSetDefinition(10989, Sys.getenv("baseUrl"))


# Fetch data -------------------------------------------------------------------
connectionDetails <- DatabaseConnector::createConnectionDetails(
  dbms = "spark",
  connectionString = keyring::key_get("databricksConnectionString"),
  user = "token",
  password = keyring::key_get("databricksToken")
)
cdmDatabaseSchema <- "merative_mdcr.cdm_merative_mdcr_v3045"
options(sqlRenderTempEmulationSchema = "scratch.scratch_mschuemi")
conceptSetData <- fetchConceptSetData(
  conceptSetExpression = conceptSetExpression, 
  connectionDetails = connectionDetails,
  cdmDatabaseSchema = cdmDatabaseSchema
)
saveRDS(conceptSetData, "e:/temp/conceptSetData.rds")

# Main condenser function ------------------------------------------------------
conceptSetData <- readRDS("e:/temp/conceptSetData.rds")
condensed <- condenseConceptSet(conceptSetData$includedConceptIds, conceptSetData$conceptsToDescendants)
condensed
library(dplyr)
conceptSetData$conceptsToDescendants |> 
  filter(conceptId == 75860) |>
  filter(!descendantConceptId %in% conceptSetData$includedConceptIds)
