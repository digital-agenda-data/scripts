from __future__ import print_function
import pysolr
import requests

solr = pysolr.Solr('http://localhost:8983/solr/scoreboardtest', timeout=60)
BASE_PATH = 'http://test.digital-agenda-data.eu'

# do not index these
EXCLUDE_PROPS = set(['inner_order', 'parent_order', 'uri'])

CUSTOM_PREFIX = {
    'source_uri': '_s',
    'notation': '_s',
    'group_notation': '_ss',
    'group_name': '_txt',  # because it is multivalued
}
# append _s
EXACT_MATCH_PROPS = set(CUSTOM_PREFIX.keys())

# multiple valued
MULTIPLE_PROPS = set(['group_notation'])


# list cubes
datasets = requests.get(BASE_PATH + '/@@datacubesForSelect').json()

for dataset in datasets['options']:
    data = requests.get(dataset['uri'] + '/dimension_metadata').json()
    if 'indicator' not in data:
        continue
    try:
        print('Indexing %s (%d records)' % (dataset['dataset'], len(data['indicator'])))
        for indicator in data['indicator']:
            obj = {}
            obj['id'] = indicator['uri']
            obj['dataset_s'] = dataset['dataset']
            keys = set(indicator.keys())
            for prop in keys.intersection(EXACT_MATCH_PROPS):
                obj[prop + CUSTOM_PREFIX[prop]] = indicator[prop]
            for prop in keys - EXCLUDE_PROPS - EXACT_MATCH_PROPS:
                # append _txt_en by default
                obj[prop + '_txt_en'] = indicator[prop]
            solr.add([obj])
    except Exception as e:
        print(type(e))
        print(e)
