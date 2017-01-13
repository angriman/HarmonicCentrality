import matplotlib.pyplot as plt
import os
import json
import sys
import math

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



topk_percentages = [0.01, 0.1, 1, 10]
values = []
nodes = []
x_axis = []
iterations = 0
absolute = []
relative = []
nodes_cut = []
if len(sys.argv) < 2:
	print('Not enough parameters')
	sys.exit()

netName = str(sys.argv[1])
for f in sorted([f for f in os.listdir("./" + netName)], key=natural_keys):
	if (f.endswith('.json')) and not f.startswith('exact.json') and not f.startswith('1.json'):
		with open("./" + netName + "/" + str(f), 'r') as json_file:
			data = json.load(json_file)

		values.append(data['Harmonics'])
		nodes.append(data['Nodes'])
		absolute.append(data['Absolute'])
		relative.append(data['Relative'])
		x_axis.append(len(data['CurrentExact']))
		iterations += 1

abs_perc_lines = []
rel_perc_lines = []
n = len(values[0])

for i in range(len(values)):
	cur_nodes = nodes[i]
	sorted_abs = []
	sorted_rel = []
	for cur_node in cur_nodes:
		sorted_abs.append(absolute[i][cur_node])
		sorted_rel.append(relative[i][cur_node])
	absolute[i] = sorted_abs
	relative[i] = sorted_rel


for p in topk_percentages:
	abs_curr_p_line = []
	rel_curr_p_line = []
	for i in range(iterations):
		cut = max(math.ceil(p * n / 100), 1)

		abs_curr_p_line.append(sum(absolute[i][0:cut]) / cut)
		rel_curr_p_line.append(sum(relative[i][0:cut]) / cut)
	abs_perc_lines.append(abs_curr_p_line)
	rel_perc_lines.append(rel_curr_p_line)


font = {'family' : 'Bitstream Vera Sans',
        'weight' : 'bold',
        'size'   : 22}

plt.rc('font', **font)

plt.figure(1)
plt.grid(True)
plt.title('Top K Average absolute error metric.')
labels = []
root = 'Top '
for p in topk_percentages:
	labels.append(root + str(p) + '%')
lines = []
count = 0
for l in abs_perc_lines:
	line, = plt.plot(x_axis, l, label=labels[count], linewidth=3)
	count += 1
	lines.append(line)
plt.xlabel('Computed BFSs')
plt.ylabel('Average absolute error')
plt.legend(handles=lines)

plt.figure(2)
plt.grid(True)
plt.title('Top K Average relative error metric.')
labels = []
root = 'Top '
for p in topk_percentages:
	labels.append(root + str(p) + '%')
lines = []
count = 0
for l in rel_perc_lines:
	line, = plt.plot(x_axis, l, label=labels[count], linewidth=3)
	count += 1
	lines.append(line)

plt.legend(handles=lines)
plt.xlabel('Computed BFSs')
plt.ylabel('Average absolute error')
plt.show()

