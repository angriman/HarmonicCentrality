import matplotlib.pyplot as plt
import os
import json
import sys
import math

def computeMargin(arr1, arr2):
	i = 0
	intersection = []
	while i < len(arr1):
		if arr1[i] != arr2[i]:
			arr = [i, intersection]
			return arr
		intersection.append(arr1[i])
		i += 1

def computeIntersection(arr1, arr2):
	max_ = int(len(arr1) * 0.1) + 1
	result = len(set(arr1[0:max_]).intersection(set(arr2[0:max_]))) 
	return result

if len(sys.argv) < 2:
	print('Network name not specified')
	sys.exit()

topk_percentages = [0.1, 1, 2, 5]
margin = [0]
intersection = [0]
prevNodes = []
x_axis = []
iteration = 0
annotations = []
netName = str(sys.argv[1])
for f in sorted([f for f in os.listdir('./' + netName)]):
	if f.endswith('.json'):
		with open('./' + netName + '/' + str(f), 'r') as json_file:
			data = json.load(json_file)

		curr_nodes = data['Nodes']
		if iteration > 0:
			result = computeMargin(curr_nodes, prevNodes)
			margin.append(result[0])
			intersection.append(computeIntersection(curr_nodes, prevNodes))
			if margin[iteration] > 0:
				annotations.append(result[1])
		prevNodes = curr_nodes
		x_axis.append(iteration)
		iteration += 1


font = {'family' : 'Bitstream Vera Sans',
        'weight' : 'normal',
        'size'   : 11}

plt.rc('font', **font)

fig = plt.figure(1)
ax = fig.add_subplot(111)
a = 0
cur_margin = 0
for m in range(len(margin)):
	if margin[m] > 0 and not margin[m] == cur_margin:
		cur_margin = margin[m]
		#ax.annotate(annotations[a], xy=(m, margin[m]), xytext=(0, margin[m]-0.5), arrowprops=dict(facecolor='black', shrink=0.05),)
		a += 1

plt.grid(True)
plt.title('Top k stability margin')
line, = plt.plot(x_axis, margin, linewidth=3)

fig2 = plt.figure(2)
plt.grid(True)
plt.plot(x_axis, intersection, linewidth=3)
plt.title('Top k intersection')
plt.show()