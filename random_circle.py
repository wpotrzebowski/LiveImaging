import matplotlib
import matplotlib.pyplot as plt
import numpy as np
from PIL import Image
from io import BytesIO

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
    while no_points <= number_of_points:
        accepted, point = getRandomPointsInCircle(inner_radius, outer_radius)
        if accepted:
            XY_points.append((point[0],point[1]))
            no_points +=1
    XY_array = np.array(XY_points)
    fig = plt.figure()
    plt.scatter(XY_array[:,0],XY_array[:,1], c='black', s=1.0)
    plt.axis('off')
    #plt.show()

    # Save the image in memory in PNG format
    png1 = BytesIO()
    fig.savefig(png1, format="png", bbox_inches='tight')

    # Load this image into PIL
    png2 = Image.open(png1)

    # Save as TIFF
    png2.save(image_name)
    png1.close()

if __name__ == "__main__":
    for i in range(100):
        name = "circle_30_"+str(i)+".tiff"
        generate_images(30, 100, 1000, name)