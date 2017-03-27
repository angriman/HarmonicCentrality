import json
import os, os.path
import re

def atoi(text):
    return int(text) if text.isdigit() else text

def natural_keys(text):
	return [ atoi(c) for c in re.split('(\d+)', text) ]


result = []
#l = [x for x in range(1,len([f for f in os.listdir('.') if os.path.isfile(f) and f.endswith(".json") ]))]
for f in sorted(os.listdir('.'), key=natural_keys):
	if os.path.isfile(f) and f.endswith('.json'):
#for f in l:
		path = str(f) #+ ".json"
		with open(path, 'r') as j:
			data = json.load(j)
			result.append(data)
		

with open("myr_result.txt", 'w') as j:
	j.write(json.dumps(result))