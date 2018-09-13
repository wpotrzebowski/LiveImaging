#!/usr/bin/env python
import matplotlib
import matplotlib.pyplot as plt
import numpy as np
from PIL import Image
from io import BytesIO
from pylab import cm

def radial_profile(data, center):
    #y,x = np.indices(data.shape) # first determine radii of all pixels
    x,y = data[:,0],data[:,1]
    r = np.sqrt((x-center[0])**2+(y-center[1])**2)
    ind = np.argsort(r.flat) # get sorted indices
    sr = r.flat[ind] # sorted radii
    sim = data.flat[ind] # image values sorted by radii
    ri = sr.astype(np.int32) # integer part of radii (bin size = 1)
    # determining distance between changes
    deltar = ri[1:] - ri[:-1] # assume all radii represented
    rind = np.where(deltar)[0] # location of changed radius
    nr = rind[1:] - rind[:-1] # number in radius bin
    csim = np.cumsum(sim, dtype=np.float64) # cumulative sum to figure out sums for each radii bin
    tbin = csim[rind[1:]] - csim[rind[:-1]] # sum for image values in radius bins
    radialprofile = tbin/nr # the answer
    return radialprofile

def getRandomPointsInCircle(inner_radius, outer_radius):
    """
    Function generates a random point inside a circle
    and returns its X and Y coordinates as a tuple.
    """
    accepted = False
    accepted_point = None
    xy_point =  np.random.uniform(-outer_radius,outer_radius, (2,1))
    if pow(xy_point[0],2)+pow(xy_point[1],2)>pow(inner_radius,2) and pow(xy_point[0],2)+pow(xy_point[1],2)<=pow(outer_radius,2):
        accepted_point = xy_point
        accepted = True
    return accepted, accepted_point

# Main function for testing
def generate_images(inner_radius, outer_radius, number_of_points, image_name):
    XY_points = []
    no_points = 0
    radi = []
    intensities = []
    mu, sigma = 0.5, 0.5
    nomral_dist = np.random.normal(mu, sigma, number_of_points)
    while no_points <= number_of_points:
        accepted, point = getRandomPointsInCircle(inner_radius, outer_radius)
        if accepted:
            XY_points.append((point[0],point[1]))
            radius = np.sqrt(pow(point[0],2)+pow(point[1],2))
            radi.append(radius)
            no_points +=1

    indexes = np.linspace(int(inner_radius),int(outer_radius),number_of_points)
    for radius in radi:
        #Closest index shifted to 0
        closest_index= int(min(indexes, key=lambda x:abs(x-radius)) - int(inner_radius))
        intensities.append(nomral_dist[closest_index-1])
    print (np.shape(intensities), np.shape(radi))
    XY_array = np.array(XY_points)
    fig = plt.figure()
    plt.scatter(XY_array[:,0], XY_array[:,1], c=np.array(radi), cmap=cm.Greys, s=1.0)
    plt.axis('off')
    #plt.show()

    # Save the image in memory in PNG format
    png1 = BytesIO()
    fig.savefig(png1, format="png", bbox_inches=0, pad_inches = 0)

    # Load this image into PIL
    png2 = Image.open(png1)

    # Save as TIFF
    png2.save(image_name)
    png1.close()

    fig2 = plt.figure()
    center, radi = (0,0), 0

    rad = radial_profile(XY_array, center)

    plt.plot(rad[radi:])
    fig2.savefig("profile_"+name[:-5]+".png", format="png")

if __name__ == "__main__":
    for i in range(1):
        name = "circle_30_5K_"+str(i)+".tiff"
        generate_images(30, 100, 70, name)