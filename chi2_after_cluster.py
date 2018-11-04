#!/usr/bin/env python
import numpy as np
import matplotlib.pyplot as plt
import sys

def calculateChi(weightedIns, expIns, use_weights = True):
    """
    Calculates chis 
    """
    mixed_term_ = 0.0
    square_calc_ = 0.0
    Sindex = 0
    if use_weights:
        weight1 = weightedIns[-1]
        weight2 = expIns[-1]
    else:
        weight1 = 1
        weight2 = 1
    weightedIns = weightedIns[:-1]
    expIns = expIns[:-1]
    #for ins in expIns:
    #    Iobs=ins
    #    mixed_term_ += Iobs*weightedIns[Sindex]
    #    square_calc_ += weightedIns[Sindex]*weightedIns[Sindex]
    #    Sindex+=1
    #scale_factor = mixed_term_/square_calc_
    scale_factor = 1
    chi2_=0.0
    square_obs_ = 0.0
    Sindex = 0
    for ins in expIns:
        Iobs=ins
        square_obs_ += Iobs*Iobs
        chi2_+=(weight2*Iobs-weight1*weightedIns[Sindex])*(weight2*Iobs-weight1*weightedIns[Sindex])
        Sindex+=1
    #print chi2_, square_obs_
    chi2_=chi2_/square_obs_
    return chi2_

def interpolate(results):
    rows,cols = np.shape(results)
    peak_x = []
    x_grid = np.linspace(0,100,100)
    fout = open("output_normalized.csv","w")
    all_yintensities = []
    for i in range(cols-1):
        row = results[:,i]
        non_zero = len(row[row>0])
        max_peak = np.argmax(row)
        peak_x.append(float(max_peak)/non_zero)
        norm_row = np.linspace(0,100,num=non_zero)
        interp_y = np.interp(x_grid, norm_row, row[row>0])
        all_yintensities.append(interp_y)
        fout.writelines(["%.2f, " % x for x in interp_y])
        fout.write("\n")
    fout.close()
    return all_yintensities, peak_x

def create_heatmap(chi2_array):
    plt.imshow(chi2_array, cmap='jet', interpolation='nearest')
    plt.savefig("heat_map.png")
    plt.show()

def generate_chis(all_yintensities_1, all_yintensities_2, use_weights):

    #all_yintensities_1, peak_x_1 = interpolate(data1)
    #all_yintensities_2, peak_x_2 = interpolate(data2)
    all_yintensities_1  = np.transpose(all_yintensities_1)
    all_yintensities_2  = np.transpose(all_yintensities_2)
    print("Chi2 calculations")
    jindex = 0
    rows1 = len(all_yintensities_1)
    rows2 = len(all_yintensities_2)
    chi2_array = np.zeros((rows1,rows2))
    cumulative_chi2 = 0
    chi2_max = -100
    excluded = 0
    accepted = 0
    for int1 in all_yintensities_1:
        iindex = 0
        for int2 in all_yintensities_2:
            #if iindex == jindex:
            chi2a =  calculateChi(int1,int2, use_weights)
            chi2b =  calculateChi(int1,int2, use_weights)
            if chi2b<chi2a:
                chi2_array[iindex][jindex] = chi2b
            else:
                chi2_array[iindex][jindex] = chi2a
            iindex+=1
        jindex+=1

    matching_indexes = []
    forbiden_indexes = []
    for i, row in enumerate(chi2_array):
        min_i = (np.argmin(row))
        index=0
        while (min_i in forbiden_indexes):
            min_i = np.argsort(row)[index]
            index+=1
        matching_indexes.append((i,min_i))
        forbiden_indexes.append(min_i)

    legend_lines = []
    colors = ['red', 'blue', 'green', 'black', 'orange', 'yellow']

    for c_i,matches in enumerate(matching_indexes):
        i1 = matches[0]
        i2 = matches[1]
        chi2_fl = round(chi2_array[i1][i2],3)
        cumulative_chi2 += chi2_fl
        line1, = plt.plot(all_yintensities_1[i1][:-1],
                          linestyle='dashed', color=colors[c_i])
        line2, = plt.plot(all_yintensities_2[i2][:-1], label="chi2 ="+str(chi2_fl),
                          color = colors[c_i])
        legend_lines.append(line1)
        legend_lines.append(line2)
    plt.legend(handles=legend_lines)

    #create_heatmap(chi2_array.transpose())
    if use_weights:
        print ("Cumulative and max chi2 using weights", cumulative_chi2)
    else:
        print ("Cumulative and max chi2 with no weights", cumulative_chi2)

    plt.show()


def generate_chis_pairs(all_yintensities_1, all_yintensities_2, use_weights):

    #all_yintensities_1, peak_x_1 = interpolate(data1)
    #all_yintensities_2, peak_x_2 = interpolate(data2)
    all_yintensities_1  = np.transpose(all_yintensities_1)
    all_yintensities_2  = np.transpose(all_yintensities_2)
    print("Chi2 pairs calculations")
    jindex = 0
    rows1 = len(all_yintensities_1)
    chi2_array = np.zeros((rows1))
    cumulative_chi2 = 0
    iindex = 0
    for int1 in all_yintensities_1:
        chi2 =  calculateChi(int1,all_yintensities_2[iindex], use_weights)
        chi2_array[iindex] = chi2
        cumulative_chi2 += chi2_array[iindex]
        iindex+=1

    if use_weights:
        print ("Cumulative and max chi2 and individual weights", cumulative_chi2, np.argmin(chi2_array))
    else:
        print ("Cumulative and max chi2 and individual weights", cumulative_chi2, np.argmin(chi2_array))

if __name__ == "__main__":
    fin = open(sys.argv[1])
    fin1 = open(sys.argv[2])
    data1 = np.genfromtxt(sys.argv[1], dtype="float64", delimiter=",")
    data2 = np.genfromtxt(sys.argv[2], dtype="float64", delimiter=",")
    #print("Comparing with weights "+sys.argv[1]+" with "+sys.argv[2])
    #generate_chis(data1,data2, True)
    print("Comparing with no weights "+sys.argv[1]+" with "+sys.argv[2])
    generate_chis(data1,data2, False)

    #print("Comparing "+sys.argv[1]+" with "+sys.argv[1])
    #generate_chis(data1,data1)
    #print("Comparing "+sys.argv[2]+" with "+sys.argv[2])
    #generate_chis(data2,data2)
