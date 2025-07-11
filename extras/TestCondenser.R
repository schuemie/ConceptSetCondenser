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
cdmDatabaseSchema <- "merative_mdcr.cdm_merative_mdcr_v3466"
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
saveRDS(conceptSetData, "e:/temp/conceptSetData.rds")


# Main condenser function ------------------------------------------------------
conceptSetData <- readRDS("e:/temp/conceptSetData.rds")
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

# ROhdsiWebApi::deleteConceptSetDefinition(11169, Sys.getenv("baseUrl"))


# Test all of Joel's concept sets ----------------------------------------------
joelsConceptSets <- c(11166, 11162, 11161, 11153, 11151, 11150, 11148, 11147, 11146, 11140, 11139, 11138, 11136, 11132, 11129, 11128, 11125, 11124, 11123, 11122, 11121, 11120, 11118, 11117, 11115, 11114, 11113, 11107, 11105, 11104, 11084, 11081, 11073, 11072, 11068, 11067, 11064, 11063, 11051, 11050, 11049, 11048, 11047, 11046, 11045, 11044, 11043, 11036, 11035, 11026, 11023, 11003, 10999, 10998, 10997, 10995, 10990, 10989, 10988, 10987, 10954, 10953, 10944, 10943, 10934, 10832, 10831, 10830, 10829, 10826, 10823, 10810, 10806, 10804, 10803, 10800, 10799, 10798, 10795, 10794, 10791, 10789, 10788, 10787, 10786, 10785, 10784, 10782, 10781, 10780, 10779, 10778, 10777, 10776, 10775, 10774, 10773, 10771, 10770, 10769, 10767, 10765, 10760, 10757, 10756, 10755, 10754, 10753, 10752, 10749, 10748, 10746, 10745, 10741, 10737, 10735, 10690, 10689, 10688, 10687, 10686, 10672, 10671, 10669, 10668, 10657, 10648, 10636, 10633, 10632, 10631, 10630, 10629, 10628, 10627, 10626, 10625, 10624, 10623, 10622, 10621, 10620, 10619, 10618, 10617, 10616, 10615, 10614, 10613, 10612, 10611, 10610, 10609, 10608, 10607, 10606, 10605, 10604, 10603, 10602, 10601, 10600, 10599, 10598, 10597, 10596, 10595, 10594, 10593, 10592, 10591, 10590, 10589, 10588, 10587, 10586, 10585, 10584, 10583, 10582, 10581, 10580, 10579, 10578, 10577, 10576, 10575, 10574, 10573, 10572, 10571, 10570, 10569, 10568, 10567, 10566, 10565, 10564, 10563, 10562, 10561, 10560, 10559, 10558, 10557, 10556, 10555, 10554, 10553, 10552, 10551, 10550, 10549, 10548, 10547, 10546, 10544, 10543, 10542, 10541, 10540, 10539, 10538, 10537, 10536, 10535, 10534, 10533, 10532, 10531, 10530, 10529, 10528, 10527, 10526, 10525, 10524, 10523, 10511, 10502, 10500, 10498, 10494, 10493, 10492, 10483, 9677)
connection <- DatabaseConnector::connect(connectionDetails)
for (i in 5:length(joelsConceptSets)) {
  conceptSetId <-  joelsConceptSets[i]
  writeLines(sprintf("Processing concept set %s (%d of %d)", conceptSetId, i, length(joelsConceptSets)))
  conceptSetExpression <- ROhdsiWebApi::getConceptSetDefinition(conceptSetId, Sys.getenv("baseUrl"))
  conceptSetData <- fetchConceptSetData(
    conceptSetExpression = conceptSetExpression, 
    connection = connection,
    cdmDatabaseSchema = cdmDatabaseSchema
  )
  saveRDS(conceptSetData, "e:/temp/conceptSetData.rds")
  # conceptSetData <- readRDS("e:/temp/conceptSetData.rds")
  condensedConceptSet <- condenseConceptSet(conceptSetData)
}
DatabaseConnector::disconnect(connection)
