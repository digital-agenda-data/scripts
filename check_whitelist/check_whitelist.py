import json
from urllib.parse import urlencode
from urllib.request import urlopen

from string import Template

WHITELIST_URL = 'http://digital-agenda-data.eu/datasets/digital_agenda_scoreboard_key_indicators/whitelist.json'
#WHITELIST_URL = 'http://localhost:8440/Plone/datasets/digital_agenda_scoreboard_key_indicators/whitelist.json'
SPARQL_ENDPOINT = 'http://virtuoso.digital-agenda-data.eu/sparql'


with open('query-obs.sparql', 'r') as file:
    template_obs = Template(file.read())

with open('query-ind-group.sparql', 'r') as file:
    template_ind_group = Template(file.read())

whitelist = json.loads(urlopen(WHITELIST_URL).read())
for row in whitelist:
    params = {}
    # query indicator groups
    params['query'] = template_ind_group.substitute(indicator=row['indicator'].lower())
    params['format'] = 'application/sparql-results+json'
    url = SPARQL_ENDPOINT + "?" + urlencode(params)
    result = json.loads(urlopen(url).read())
    ind_group = result['results']['bindings'][0]['groupnotation']['value']
    if row['indicator-group'].lower() != ind_group.lower():
        print("Different indicator group for " + row['indicator'] + " actual: " + ind_group)

    # query observations
    params['query'] = template_obs.substitute(
        indicator=row['indicator'].lower(), 
        breakdown=row['breakdown'].lower(), 
        unit=row['unit-measure'].lower()
    )
    params['format'] = 'application/sparql-results+json'
    url = SPARQL_ENDPOINT + "?" + urlencode(params)

    result = json.loads(urlopen(url).read())
    count = int(result['results']['bindings'][0]['callret-0']['value'])
    if (count == 0):
        print("No observations for: " + str(row))
