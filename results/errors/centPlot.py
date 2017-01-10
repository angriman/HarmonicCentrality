import matplotlib.pyplot as plt
import os
import json
import sys
import math
# with open('./facebook/errors10.json', 'r') as json_file:
# 	data = json.load(json_file)
# with open('./facebook/exact.json', 'r') as json_file:
# 	gt = json.load(json_file)

# centralities = sorted(data['Centralities'], reverse=True)
# centralities = [x / len(centralities) for x in centralities]
# annotations = [str(x)[0:5] for x in centralities]
# x_ax = [x for x in range(len(centralities))]
topk_perc = [0.01, 0.1, 1, 5 , 10]

def checkIntersection(sub, total_set):
	result = []
	for p in topk_perc:
		cut = int(p * len(total_set) / 100) + 1
		result.append(len(set(sub).intersection(set(total_set[0:cut]))) / cut)
	return result

import re

def atoi(text):
    return int(text) if text.isdigit() else text

def natural_keys(text):
    '''
    alist.sort(key=natural_keys) sorts in human order
    http://nedbatchelder.com/blog/200712/human_sorting.html
    (See Toothy's implementation in the comments)
    '''
    return [ atoi(c) for c in re.split('(\d+)', text) ]


x_ax = []
gt = []
gtNodes = []
legend = []
n = 0
samples = []
correctly_detected = []
c = 0
file_list = [f for f in os.listdir("./facebook/")]
for f in sorted(file_list, key=natural_keys):
	if f.endswith('.json') and not f.startswith('exact'):
		with open("./facebook/" + str(f), 'r') as json_file:
			data = json.load(json_file)

		gtNodes = data["GTNodes"]
		n = len(gtNodes)
		curr = data['CurrentExact']
		result = checkIntersection(curr, gtNodes)
		correctly_detected.append(result)
		#x_ax.append(len(curr))
		samples.append(len(curr))

x_ax = [x for x in range(len(correctly_detected))]
font = {'family' : 'Bitstream Vera Sans',
        'weight' : 'bold',
        'size'   : 14}


plt.rc('font', **font)
plt.figure(1)
plt.grid(True)
lines = []	
for p in range(len(topk_perc)):
	line = []
	legend.append('top ' + str(topk_perc[p]) + '% ~ ' + str(int(n * topk_perc[p] / 100) + 1))
	for c in correctly_detected:
		line.append(c[p])
	lines.append(line)

plt_lines = []
c = 0
for l in lines:
	current_line, = plt.plot(samples, l, label=legend[c], linewidth=3)
	c += 1
	plt_lines.append(current_line)
plt.legend(handles=plt_lines,loc=1)
#plt.xticks(x_ax, samples)
plt.title('Greedy sampling')
plt.xlabel('Computed BFS')
plt.ylabel('Precision among Top K')
plt.show()

# fig = plt.figure(1)

# ax = fig.add_subplot(111)
# c = 0
# # for a in annotations:
# # 	ax.annotate(a, xy=(c, float(a)))
# # 	c += 1
# plt.plot(x_ax, centralities, '*', linewidth=3)
# plt.grid(True)

# plt.figure(2)
# plt.grid(True)
# plt.hist(centralities, bins=100)


#plt.show()