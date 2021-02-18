from bs4 import BeautifulSoup
import requests
import json

DOMAIN = 'digital-agenda-data.eu'

DATASETS = (
    'desi',
    'digital_agenda_scoreboard_key_indicators',
    'e-gov',
    'egov-1',
)


for dataset in DATASETS:
    url = f"https://{DOMAIN}/datasets/{dataset}/visualizations"
    content = requests.get(url).text
    soup = BeautifulSoup(content, 'html.parser')
    counter = 1
    for link in soup.select('tr.scoreboard-visualizations-listing-item td.thumbnail-area a[href]'):
        link_config = link.get('href') + '/config.json'
        chart = link_config.split('/')[4]
        config = json.loads(requests.get(link_config).text)
        filename = f"{dataset}__{counter}__{chart}__config.json"
        with open(filename, 'w', encoding='utf-8', newline='\n') as f:
            json.dump(config, f, indent=4, ensure_ascii=False, sort_keys=True) 
        counter += 1




