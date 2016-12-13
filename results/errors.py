import matplotlib.pyplot as plt
import os
import json

files = [f for f in os.listdir('.') if os.path.isfile(f)]
y_abs = []
y_rel = []
x_axis = []
iteration = 1
for f in files:
	if (f.endswith('.json')):
		with open(f, 'r') as json_file:
			data = json.load(json_file)

		absolute = data['Absolute'][0]
		relative = data['Relative'][0]
		x_axis.append(iteration)
		y_abs.append(sum(absolute) / len(absolute))
		y_rel.append(sum(relative) / len(relative))
		iteration += 1

plt.figure(1)
plt.ylabel('Absolute error')
plt.xlabel('Iteration')
plt.plot(x_axis, y_abs)
plt.show()
