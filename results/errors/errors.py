import matplotlib.pyplot as plt
import os
import json
import sys
import math
from enum import Enum

class Graph(Enum):
	Actual = 0
	Metric = 1

if len(sys.argv) < 3:
	print('Not enough parameters')
	sys.exit()
netName = str(sys.argv[1])
graphType = Graph.Actual
arg2Name = sys.argv[2]
if arg2Name == '1':
	graphType = Graph.Metric

values = []
nodes = []
files = sorted([f for f in os.listdir("./" + netName)])
y_abs = []
y_rel = [] 
max_abs = []
max_rel = []
x_axis = []
iteration = 1
for f in files:
	if (f.endswith('.json') and not f.startswith('errors1')):
		with open("./"+netName+"/"+str(f), 'r') as json_file:
			data = json.load(json_file)

		absolute = []
		relative = []
		if graphType == Graph.Actual:
			absolute = data['RealAbsolute']
			relative = data['RealRelative']
		elif graphType == Graph.Metric:
			absolute = data['Absolute']
			relative = data['Relative']

		y_abs.append(sum(absolute) / len(absolute))
		y_rel.append(sum(relative) / len(relative))
		max_abs.append(max(absolute))
		max_rel.append(max(relative))
		iteration += 1
		x_axis.append(iteration)

plt.figure(1)
plt.grid(True)
plt.ylabel('Absolute/relative error')
plt.xlabel('Iteration')
label = 'Avg. abs. error'
if len(sys.argv) <= 2:
	label += ' metric'
line1, = plt.plot(x_axis, y_abs, label=label, linewidth=3)
#line2, = plt.plot(x_axis, max_abs, label='Max abs. error', linewidth=3)
plt.legend(handles=[line1], loc=1)
plt.ticklabel_format(style='sci', axis='x', scilimits=(0,0))

plt.figure(2)
plt.grid(2)
plt.xlabel('Iteration')
label = 'Avg. rel. error'
if len(sys.argv) <= 2:
	label += ' metric'
line1, = plt.plot(x_axis, y_rel, '--', label=label, linewidth=3)
#line2, = plt.plot(x_axis, max_rel, '--', label='Max rel. error', linewidth=3)
plt.legend(handles=[line1], loc=1)
plt.ticklabel_format(style='sci', axis='x', scilimits=(0,0))

	
plt.show()
