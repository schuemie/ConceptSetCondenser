ConceptSetCondenser
===================

An R package (wrapping Java code) for condensing a concept set. This will find the shortest concept set expression that still includes exactly the same concepts as the original concept set.


Examples
========

```r
library(ConceptSetCondenser)
connectionDetails <- createConnectionDetails(dbms="postgresql", 
                                             server="localhost",
                                             user="root",
                                             password="blah")
cdmDatabaseSchema <- "merative_mdcr.cdm_merative_mdcr_v3045"

conceptSetExpression <- ROhdsiWebApi::getConceptSetDefinition(123, Sys.getenv("baseUrl"))
conceptSetData <- fetchConceptSetData(
  conceptSetExpression = conceptSetExpression, 
  connectionDetails = connectionDetails,
  cdmDatabaseSchema = cdmDatabaseSchema
)
condensedConceptSet <- condenseConceptSet(conceptSetData)
ROhdsiWebApi::postConceptSetDefinition(
  name = "My condensed concept set",
  conceptSetDefinition = condensedConceptSet,
  baseUrl = Sys.getenv("baseUrl")
)
```


Technology
============

ConceptSetCondenser is an R package wrapping some custom Java code.


System Requirements
===================

Running the package requires R with the package rJava installed. Also requires Java 1.8 or higher.


Installation
============

1. See the instructions [here](https://ohdsi.github.io/Hades/rSetup.html) for configuring your R environment, including Java.

2. To install the latest stable version, install from GitHub:

```r
remotes::install_github("schuemie/ConceptSetCondenser")
```


License
=======

ConceptSetCondenser is licensed under Apache License 2.0. 


Development
===========

ConceptSetCondenser is being developed in R Studio and Eclipse.


### Development status

ConceptSetCondenser is under development. Only used if Martijn told you so.


