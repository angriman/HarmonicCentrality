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


# Max centrality 4069.316666667301
# Node 4587

topk_perc = [0.01, 0.1, 1, 5 , 10]
n = 0
def checkStability(sampled, prev):
	result = []
	for p in topk_perc:
		cut = int(p * n / 100) + 1
		result.append(len(set(sampled[0:cut]).intersection(set(prev[0:cut]))) / cut)

	return result

# def stableNodes(iteration, pool):
# 	if iteration == len(pool)-1:
# 		return len(pool[len(pool)-1])
# 	s = 0
# 	for i in range(len(pool[iteration])):
# 		first = pool[iteration][i]
# 		for j in range(iteration+1, len(pool)):
# 			if not first == pool[j][i]:
# 				return s
# 		s += 1
# 	return s

def stableNodes(pool):
	result = [0 for x in range(len(pool))]
	for i in range(len(pool[len(pool) - 1])):
		first = pool[len(pool)-1][i]
		for j in range(len(pool)-1, 0, -1):
			if i >= len(pool[j]) or not pool[j][i] == first:
				break
			else:
				result[j] += 1
				first = pool[j][i]
	return result



netName = "wordassociation-2011"
x_ax = []
legend = []
gt = 0
iteration = 0
prev = []
stability = []
stable_nodes = []
pool = []
samples = 0
file_list = [f for f in os.listdir("./"+netName+"/")]
for f in sorted(file_list, key=natural_keys):
	if f.endswith('.json') and not f.startswith('exact'):
		with open("./"+netName+"/" + str(f), 'r') as json_file:
			data = json.load(json_file)

		current_exact = data['CurrentExact']
		pool.append(current_exact)
		if iteration > 0:
			stability.append(checkStability(current_exact, prev))
		else:
			stability.append([0 for x in topk_perc])
			stable_nodes.append(0)
			n = len(data['GT'])
			samples = len(data['CurrentExact'])

		# prev = current_exact
		iteration += 1
		x_ax.append(iteration * samples)
		#x_ax.append(iteration)

#for i in range(1, iteration):
#	stable_nodes.append(stableNodes(i, pool))
stable_nodes = stableNodes(pool)


font = {'family' : 'Bitstream Vera Sans',
        'weight' : 'bold',
        'size'   : 14}

plt.rc('font', **font)
# plt.figure(1)
# plt.grid(True)
# legend = []
# lines = []
# for p in range(len(topk_perc)):
# 	line = []
# 	legend.append('top ' + str(topk_perc[p]) + '% ~ ' + str(int(n * topk_perc[p] / 100) + 1))
# 	for c in stability:
# 		line.append(c[p])
# 	lines.append(line)

# plt_lines = []
# c = 0
# for l in lines:
# 	current_line, = plt.plot(x_ax, l, label=legend[c], linewidth=2)
# 	c += 1
# 	plt_lines.append(current_line)

# plt.legend(handles=plt_lines, loc=4)
# plt.xlabel("Computed BFSs")
# plt.ylabel("TopK stability")

plt.figure(2)
plt.grid(True)
plt.plot(x_ax[0:len(x_ax)], stable_nodes[0:len(stable_nodes)], linewidth=3)
plt.xlabel("Computed BFSs")
plt.ylabel("Overall stability")
plt.show()