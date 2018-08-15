import numpy as np
import matplotlib.pyplot as plt
import sys
from sklearn.metrics import classification_report
import random

def DTWDistance(s1, s2,w):
    DTW={}

    w = max(w, abs(len(s1)-len(s2)))

    for i in range(-1,len(s1)):
        for j in range(-1,len(s2)):
            DTW[(i, j)] = float('inf')
    DTW[(-1, -1)] = 0

    for i in range(len(s1)):
        for j in range(max(0, i-w), min(len(s2), i+w)):
            dist= (s1[i]-s2[j])**2
            DTW[(i, j)] = dist + min(DTW[(i-1, j)],DTW[(i, j-1)], DTW[(i-1, j-1)])

    return np.sqrt(DTW[len(s1)-1, len(s2)-1])

def LB_Keogh(s1,s2,r):
    LB_sum=0
    for ind,i in enumerate(s1):

        lower_bound=min(s2[(ind-r if ind-r>=0 else 0):(ind+r)])
        upper_bound=max(s2[(ind-r if ind-r>=0 else 0):(ind+r)])

        if i>upper_bound:
            LB_sum=LB_sum+(i-upper_bound)**2
        elif i<lower_bound:
            LB_sum=LB_sum+(i-lower_bound)**2

    return np.sqrt(LB_sum)

def k_means_clust(data,num_clust,num_iter,w=5):
    centroids=random.sample(list(data),num_clust)
    counter=0
    for n in range(num_iter):
        counter+=1
        print(counter)
        assignments={}
        #assign data points to clusters
        for ind,i in enumerate(data):
            min_dist=float('inf')
            closest_clust=None
            for c_ind,j in enumerate(centroids):
                if LB_Keogh(i,j,5)<min_dist:
                    cur_dist=DTWDistance(i,j,w)
                    if cur_dist<min_dist:
                        min_dist=cur_dist
                        closest_clust=c_ind
            if closest_clust in assignments:
                assignments[closest_clust].append(ind)
            else:
                assignments[closest_clust]=[]

        #recalculate centroids of clusters
        clust_sums = []
        for key in assignments:
            clust_sum=0
            for k in assignments[key]:
                clust_sum=clust_sum+data[k]
            centroids[key]=[m/len(assignments[key]) for m in clust_sum]
            clust_sums.append(len(assignments[key]))
    return centroids, clust_sums

def remove_negatives(results):
    """
    Removing "negative curves from data set"
    :param results:
    :return:
    """
    rows,cols = np.shape(results)
    removed = 0
    cleaned_results = results
    for i in range(cols-1):
        row = results[:,i]
        row = row[row>0]
        starting_point = row[0]
        non_zero = len(row)
        lesser = len(row[row<starting_point])
        if float(lesser)/non_zero>0.9:
            cleaned_results = np.delete(cleaned_results, i-removed, axis=1)
            removed+=1
    print("Removed negative curves:", removed)
    return cleaned_results

def interpolate(results):
    rows,cols = np.shape(results)
    peak_x = []
    x_grid = np.linspace(0,100,100)
    #fout = open("output_normalized.csv","w")
    all_yintensities = []
    for i in range(cols-1):
        row = results[:,i]
        non_zero = len(row[row>0])
        max_peak = np.argmax(row)
        peak_x.append(float(max_peak)/non_zero)
        norm_row = np.linspace(0,100,num=non_zero)
        interp_y = np.interp(x_grid, norm_row, row[row>0])
        all_yintensities.append(interp_y)
        #fout.writelines(["%.2f, " % x for x in interp_y])
        #fout.write("\n")
    #fout.close()
    return all_yintensities, peak_x


fname = sys.argv[1]
fin1 = open(fname)
results = np.genfromtxt(fname, dtype="float64", delimiter=",")
print("Input size", np.shape(results))
cleaned_results = remove_negatives(results)
print("Size after cleanup", np.shape(cleaned_results))
all_yintensities, peak_x = interpolate(cleaned_results)

number_of_clusters = int(sys.argv[2])

data=np.vstack((all_yintensities))
print(np.shape(data))
centroids, clust_sums =k_means_clust(data,number_of_clusters,20,4)
legend_lines = []
maximums = np.amax(centroids, axis=1)
max_indexes = np.argsort(maximums).flatten()
#Sorting centroids
#centroids = centroids[np.argsort(maximums)
cen_to_save = np.empty(np.shape(centroids))
clust_sums_to_save = np.empty(np.shape(clust_sums))
gb_index = 0
for i in max_indexes:
    centroid = []
    for j in range(np.shape(centroids)[1]):
        centroid.append(centroids[i][j])
        cen_to_save[gb_index][j] = centroids[i][j]
        clust_sums_to_save[gb_index]  = clust_sums[i]
    line1, = plt.plot(np.array(centroid), label = "Members: "+str(clust_sums[i]))
    legend_lines.append(line1)
    plt.legend(handles=legend_lines)
    gb_index +=1
#print np.shape(cen_to_save)
#print np.shape(clust_sums)
cen_members = np.column_stack((cen_to_save,clust_sums_to_save/np.sum(clust_sums_to_save)))
np.savetxt(fname[:-4]+"_centroids.csv", np.transpose(cen_members), delimiter=",")
plt.savefig(fname[:-4]+"_centroids.png")
plt.show()
