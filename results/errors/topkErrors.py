import matplotlib.pyplot as plt
import os
import json
import sys
import math

topk_percentages = [0.1, 1, 2, 5]
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
for f in sorted([f for f in os.listdir("./" + netName)]):
	if (f.endswith('.json')) and not f.startswith('errors1.json'):
		with open("./" + netName + "/" + str(f), 'r') as json_file:
			data = json.load(json_file)

		values.append(data['Harmonics'])
		nodes.append(data['Nodes'])
		absolute.append(data['Absolute'])
		relative.append(data['Relative'])
		x_axis.append(iterations)
		iterations += 1

abs_perc_lines = []
rel_perc_lines = []
n = len(values[0])
for p in topk_percentages:
	abs_curr_p_line = []
	rel_curr_p_line = []
	for i in range(iterations):
		cut = max(math.ceil(p * n / 100), 1)
		abs_curr_p_line.append(sum(absolute[i][0:cut]) / cut)
		rel_curr_p_line.append(sum(relative[i][0:cut]) / cut)
	abs_perc_lines.append(abs_curr_p_line)
	rel_perc_lines.append(rel_curr_p_line)


font = {'family' : 'normal',
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
plt.show()

