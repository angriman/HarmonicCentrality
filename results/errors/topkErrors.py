import matplotlib.pyplot as plt
import os
import json
import sys
import math

topk_percentages = [0.01, 0.1, 1, 5]
percentage = 1
cut = 0
values = []
nodes = []
x_axis = []
iteration = 0
absolute = []
relative = []
if len(sys.argv) < 2:
	print('Not enough parameters')
	sys.exit()

netName = str(sys.argv[1])
for f in sorted([f for f in os.listdir("./" + netName)]):
	if (f.endswith('.json')) and not f.startswith('errors1'):
		with open("./" + netName + "/" + str(f), 'r') as json_file:
			data = json.load(json_file)

		h = data['Harmonics']
		n = data['Nodes']
		cut = math.ceil(topk_percentages[percentage] * len(h) / 100)
		values.append(h)
		nodes.append(n)
		absolute.append(data['Absolute'])
		relative.append(data['Relative'])
		iteration += 1
		x_axis.append(iteration)


plt.figure(1)
plt.grid(True)
plt.ylabel('TopK Absolute error metric')

# plt.ylabel('Harmonic Centrality estimation')
# plt.xlabel('Iteration')
# plt.title('Top ' + str(cut) + ' centralities')
abs_lines = []
rel_lines  =[]
# labels = []
nodes_union = set()
for n in nodes:
	nodes_union = nodes_union.union(set(n[0:cut]))
nodes_union = list(nodes_union)
for c in nodes_union:
	abs_line = []
	rel_line = []
	iteration = 0
	# for a in relative:
	# 	count = 0
	# 	while not nodes[iteration][count] == c:
	# 		count += 1
	# 	abs_line.append(relative[iteration][count])
	# 	iteration += 1
	# abs_lines.append(abs_line)
	for vs in values:
		count = 0
		while not nodes[iteration][count] == c:
			count += 1
		abs_line.append(values[iteration][count])
		iteration += 1
	abs_lines.append(abs_line)
	#labels.append(str(c))

# pltLines = []
# count = 0
for l in abs_lines:
	line, = plt.plot(x_axis, l, linewidth=2)
plt.title('Top ' + str(cut) + ' rel. error metric')
	# count += 1
	# pltLines.append(line)
	#plt.legend(handles=pltLines, loc=9)

# plt.figure(2)
# plt.grid(True)
# plt.xlabel('Iteration')
# plt.ylabel('Top k set size')
# size = []
# nodes_union = set()
# for n in nodes:
# 	nodes_union.update(set(n[0:cut]))
# 	size.append(len(nodes_union))
# plt.plot(x_axis, size, linewidth=3)
# plt.title('Top ' + str(cut) + ' centralities')
	
plt.show()
