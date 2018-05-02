import numpy as np
import matplotlib.pyplot as plt
import sys

def calculateChi(weightedIns, expIns):
    """
    Calculates chis 
    """
    mixed_term_ = 0.0
    square_calc_ = 0.0
    for Sindex, ins in enumerate(expIns):
        Iobs=ins
        mixed_term_ += Iobs*weightedIns[Sindex]
        square_calc_ += weightedIns[Sindex]*weightedIns[Sindex]

    scale_factor = mixed_term_/square_calc_
    scale_factor = 1
    chi2_=0.0
    square_obs_ = 0.0
    for Sindex, ins in enumerate(expIns):
        Iobs=ins
        square_obs_ += Iobs*Iobs
        chi2_+=(Iobs-scale_factor*weightedIns[Sindex])*(Iobs-scale_factor*weightedIns[Sindex])
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

def generate_chis(all_yintensities_1, all_yintensities_2):

    #all_yintensities_1, peak_x_1 = interpolate(data1)
    #all_yintensities_2, peak_x_2 = interpolate(data2)
    print "Chi2 calculations"
    jindex = 0
    rows1 = np.shape(all_yintensities_1)[0]
    rows2 = np.shape(all_yintensities_2)[0]
    cols1 = np.shape(all_yintensities_1)[1]
    cols2 = np.shape(all_yintensities_2)[1]
    chi2_array = np.zeros((cols1,cols2))
    cumulative_chi2 = 0
    chi2_max = -100
    excluded = 0
    accepted = 0
    for i in range(cols1):
        iindex = 0
        int1 = all_yintensities_1[:,i]
        for j in range(cols2):
            if iindex >= jindex:
                continue
            int2 = all_yintensities_2[:,j]
            chi2 =  calculateChi(int1,int2)
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

    create_heatmap(chi2_array.transpose())
    print "Cumulative and max", cumulative_chi2, chi2_max, accepted, excluded

if __name__ == "__main__":
    fin = open(sys.argv[1])
    fin1 = open(sys.argv[2])
    data1 = np.genfromtxt(fin, dtype="float64", delimiter=",")
    data2 = np.genfromtxt(fin1, dtype="float64", delimiter=",")
    print("Comparing "+sys.argv[1]+" with "+sys.argv[2])
    generate_chis(data1,data2)
    print("Comparing "+sys.argv[1]+" with "+sys.argv[1])
    generate_chis(data1,data1)
    print("Comparing "+sys.argv[2]+" with "+sys.argv[2])
    generate_chis(data2,data2)
