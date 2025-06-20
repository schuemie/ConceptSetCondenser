library(testthat)
library(ConceptSetCondenser)

test_that("condenseConceptSet", {
  includedConceptIds <- c(1, 2, 3, 4)
  conceptsToDescendants <- data.frame(
    conceptId = c(1, 1, 1, 1, 2, 2, 3, 4 ,5),
    descendantConceptId = c(1, 2, 3, 5, 2, 3, 3, 4, 5)
  )
  expression <- condenseConceptSet(includedConceptIds, conceptsToDescendants)
  expect_equal(length(expression), 3)
})
