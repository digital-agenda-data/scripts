#!/bin/bash

DIMENSIONS="indicator indicator-group breakdown breakdown-group source unit-measure"
for dimension in ${DIMENSIONS}
do
	GRAPH="<http://semantic.digital-agenda-data.eu/codelist/${dimension}/>"
	QUERY="construct {?s ?p ?o}where {graph ${GRAPH} {?s ?p ?o}}"
	echo $QUERY
	curl --data-urlencode "format=text/plain" --data-urlencode "query=${QUERY}" http://digital-agenda-data.eu/sparql | LC_ALL=C sort -n > dad-${dimension}.nt
done

git add -u && git commit -m "Codelist updates" && git push
