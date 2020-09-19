import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.awt.image.Raster;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.TreeSet;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.filechooser.FileFilter;

public class Harris extends Component implements KeyListener {

    private BufferedImage in, out;
    int width, height;
    File inputFile;

    //a gaussian kernel template of 3x3
    public static final float[] gaussian3x3 = {
            1/16f, 1/8f, 1/16f,
            1/8f, 1/4f, 1/8f,
            1/16f, 1/8f, 1/16f
    };

    public Harris() {
        loadImage();
        addKeyListener(this);

    }

    public Dimension getPreferredSize() {
        return new Dimension(width, height);
    }

    public void paint(Graphics g) {
        g.drawImage(out, 0, 0, null);
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Image Processing");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        Harris img = new Harris();
        frame.add("Center", img);
        frame.pack();
        img.requestFocusInWindow();
        frame.setVisible(true);

    }


    private void process() { //image filtering using filter()
        //first step:
        //apply gaussian filter to deter noise
        BufferedImageOp op = null;
        op = new ConvolveOp(new Kernel(3, 3, gaussian3x3),
                ConvolveOp.EDGE_NO_OP, null);

        out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        op.filter(in, out);
        //end


        //second step:
        //calculate the gray value for each pixel
        float[] grayArray = new float[width*height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color pixel = new Color(out.getRGB(x, y));    // get the color
                int r = pixel.getRed();// red component
                int g = pixel.getGreen();//green
                int b = pixel.getBlue();//blue
                grayArray[y * width + x] = (float) (0.299 * r + 0.587 * g + 0.114 * b); //grayscale
                int d=(int)grayArray[y*width+x];
                out.setRGB(x, y, (new Color(d,d,d)).getRGB());
            }
        }
        //end


        //third step:
        //calculate the gradient for each pixel: simple
        float[] gradientX = new float[(width)*(height)];
        float[] gradientY = new float[(width)*(height)];
        float[] gradientXY = new float[(width)*(height)];
        System.out.print("height: "+height+" and width: "+width);

        for(int y = 0; y <= height-1; y++){
            for(int x = 0; x <= width-1; x++){
                //calculate gradient of x direction
                if(x!=width-1)
                    gradientX[y * width + x] = grayArray[y * width + x + 1] - grayArray[y * width + x];
                else
                    gradientX[y * width + x] = 0;
                //calculate gradient of y direction
                if(y!=height-1)
                    gradientY[y * width + x] = grayArray[(y + 1) * width + x] - grayArray[y * width + x];
                else
                    gradientY[y * width + x] = 0;

                //square root of (square(gradientX)+square(gradientY))
                gradientXY[y * width + x] = (float) Math.sqrt(Math.pow(gradientX[y * width + x], 2) + Math.pow(gradientY[y * width + x], 2));

                int d=(int)gradientXY[y*width+x];
                d = Math.max(0, d);
                d = Math.min(255, d);
                //out.setRGB(x, y, (new Color(d,d,d)).getRGB());
            }
        }
        //end


        //fourth step:
        //calculate respond value for each pixel
        float respond[]=new float[width*height];
        for(int y = 1; y < height-1; y++) {
            for (int x = 1; x < width - 1; x++) {
                float det=0;
                float trace=0;
                float sum_gradientX=0;
                float sum_gradientY=0;
                float sum_gradientXY=0;
                float [] gradientXNeighbour = {
                        gradientX[(y-1)*width+x-1], gradientX[(y-1)*width+x], gradientX[(y-1)*width+x+1],
                        gradientX[y*width+x-1], gradientX[y*width+x], gradientX[y*width+x+1],
                        gradientX[(y+1)*width+x-1], gradientX[(y+1)*width+x], gradientX[(y+1)*width+x+1]
                };
                float [] gradientYNeighbour = {
                        gradientY[(y-1)*width+x-1], gradientY[(y-1)*width+x], gradientY[(y-1)*width+x+1],
                        gradientY[y*width+x-1], gradientY[y*width+x], gradientY[y*width+x+1],
                        gradientY[(y+1)*width+x-1], gradientY[(y+1)*width+x], gradientY[(y+1)*width+x+1]
                };
                for(int i = 0; i<9; i++) {
                    sum_gradientX = (float) (sum_gradientX + Math.pow(gradientXNeighbour[i],2) * gaussian3x3[i]);
                    sum_gradientY = (float) (sum_gradientY + Math.pow(gradientYNeighbour[i],2) * gaussian3x3[i]);
                    sum_gradientXY = sum_gradientXY + (gradientXNeighbour[i]  * gradientYNeighbour[i] * gaussian3x3[i]);
                }

                //-----------------justify a and threshold value here------------------
                float respondContstant=0.04f;
                det= sum_gradientX*sum_gradientY-sum_gradientXY*sum_gradientXY;
                trace = sum_gradientX+sum_gradientY;
                respond[y*width+x] = (float) (det - respondContstant * Math.pow(trace,2));
                if(respond[y*width+x]<2500) {
                    //out.setRGB(x, y, (new Color(255, 0, 0)).getRGB());
                    respond[y*width+x]=0;
                }
                //----------------------------------------------------------------------
            }
        }
        //end


        //fifth step:
        //nonmaximum suppression:find and only show maximum value in 3x3 pixel
        for(int y = 1; y < height-1; y++) {
            for (int x = 1; x < width - 1; x++) {
                float [] respondNeighbour = {
                        respond[(y-1)*width+x-1], respond[(y-1)*width+x], respond[(y-1)*width+x+1],
                        respond[y*width+x-1], respond[y*width+x], respond[y*width+x+1],
                        respond[(y+1)*width+x-1], respond[(y+1)*width+x], respond[(y+1)*width+x+1]
                };
                float max_value=0;
                for (int i=0; i<9; i++){
                    if(respondNeighbour[i]>max_value){
                        max_value=respondNeighbour[i];
                    }else{
                        int a= i/3;
                        int b= i%3;
                        respond[(y+a-1)*width+x+b-1]=0;

                    }
                }
            }
        }
        for(int y = 1; y < height-1; y++) {
            for (int x = 1; x < width - 1; x++) {
                if(respond[y*width+x]!=0)
                    out.setRGB(x, y, (new Color(255, 0, 0)).getRGB());
            }
        }
        //end

        repaint();
    }


    @Override
    public void keyPressed(KeyEvent ke) {
        if (ke.getKeyCode() == KeyEvent.VK_ESCAPE)
            System.exit(0);

        if (ke.getKeyChar() == 's' || ke.getKeyChar() == 'S') {// Save the processed image
            saveImage();
        } else if (ke.getKeyChar() == 'p' || ke.getKeyChar() == 'P') {// Image Processing
            process();
        }
    }

    private void loadImage() {
        try {
            in = ImageIO.read(new File("Room.jpg"));
            width = in.getWidth();
            height = in.getHeight();
            if (in.getType() != BufferedImage.TYPE_INT_RGB) {
                BufferedImage bi2 =
                        new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                Graphics big = bi2.getGraphics();
                big.drawImage(in, 0, 0, null);
                out = in = bi2;
            }
//			out = in;
        } catch (IOException e) {
            System.out.println("Image could not be read");
            System.exit(1);
        }
    }

    private void saveImage() {
        try {
            ImageIO.write(out, "jpg", new File("RoomG.jpg"));
        } catch (IOException ex) {
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        // TODO Auto-generated method stub

    }

    @Override
    public void keyTyped(KeyEvent e) {
        // TODO Auto-generated method stub

    }
}