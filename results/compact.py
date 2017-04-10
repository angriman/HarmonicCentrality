import json
import os, os.path
import re

def atoi(text):
    return int(text) if text.isdigit() else text

def natural_keys(text):
	return [ atoi(c) for c in re.split('(\d+)', text) ]


result = []
for f in sorted(os.listdir('.'), key=natural_keys):
	if os.path.isfile(f) and f.endswith('.json'):
		path = str(f)
		with open(path, 'r') as j:
			data = json.load(j)
			result.append(data)


with open("myr_result.txt", 'w') as j:
	j.write(json.dumps(result))