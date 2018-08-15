#!/usr/bin/env python
import numpy as np
#import matplotlib.pyplot as plt
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
            if iindex == jindex:
                chi2 =  calculateChi(int1,int2, use_weights)
            #if chi2>1:
            #    excluded+=1
            #    continue
            #accepted +=1
                chi2_array[iindex][jindex] = chi2
                cumulative_chi2 += chi2
                if chi2>chi2_max:
                    chi2_max = chi2
            iindex+=1
        jindex+=1

    #create_heatmap(chi2_array.transpose())
    if use_weights:
        print ("Cumulative and max chi2 using weights", cumulative_chi2, chi2_max)
    else:
        print ("Cumulative and max chi2 with no weights", cumulative_chi2, chi2_max)

if __name__ == "__main__":
    fin = open(sys.argv[1])
    fin1 = open(sys.argv[2])
    data1 = np.genfromtxt(fin, dtype="float64", delimiter=",")
    data2 = np.genfromtxt(fin1, dtype="float64", delimiter=",")
    print("Comparing with weights "+sys.argv[1]+" with "+sys.argv[2])
    generate_chis(data1,data2, True)
    print("Comparing with no weights "+sys.argv[1]+" with "+sys.argv[2])
    generate_chis(data1,data2, False)

    #print("Comparing "+sys.argv[1]+" with "+sys.argv[1])
    #generate_chis(data1,data1)
    #print("Comparing "+sys.argv[2]+" with "+sys.argv[2])
    #generate_chis(data2,data2)
