package PHash;

import org.apache.hadoop.hive.ql.exec.UDF;
import org.apache.hadoop.io.Text;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PHashIJ extends UDF{
    private static BufferedImage resize(BufferedImage image, int width,    int height) {
        BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resizedImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(image, 0, 0, width, height, null);
        g.dispose();
        return resizedImage;
    }
    private ColorConvertOp colorConvert = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);

    private BufferedImage grayscale(BufferedImage img) {
        colorConvert.filter(img, img);
        return img;
    }

    private static int getBlue(BufferedImage img, int x, int y) {
        return (img.getRGB(x, y)) & 0xff;
    }

    public Text evaluate(Text ... values){
        Text phashLength = new Text("");
        if(values.length == 0){
            return null;
        } else if (values.length > 1){
            phashLength = values[1];
        }
        Text main_img_url = values[0];


        if (main_img_url == null ) return null;
        int size = 32;
        int smallerSize = 8;
        if (phashLength.toString().equals("16")) {
            size = 64;
            smallerSize = 16;
        } else{
            size = 32;
            smallerSize = 8;
        }
        Text result = null;
        try {
            // System.out.println("Here!");

            double [] c = new double[size];

            for(int i=1; i<size; i++){
                c[i] = 1;
            }
            c[0] = 1/Math.sqrt(2.0);
            // System.out.println("c init");

            // read image
            BufferedImage img = ImageIO.read(new URL(main_img_url.toString()));
            img = resize(img, size, size);

            img = grayscale(img);

            double[][] vals = new double[size][size];

            for (int x = 0; x < img.getWidth(); x++) {
                for (int y = 0; y < img.getHeight(); y++) {
                    vals[x][y] = getBlue(img, x, y);
                }
            }

            // System.out.println(img.getHeight());

//            ImagePlus imp = new ImagePlus(main_img_url.toString());
//            System.out.println(imp.getChannel());
//            // Convert to 8-bit Gray
//            ImageConverter ic = new ImageConverter(imp);
//            ic.convertToGray8();
//            // Resize to 64x64
//            ImageProcessor ip = imp.getProcessor();
//            ip = ip.resize(size, size, true);
//            // Update to imp
//            imp.setProcessor(imp.getTitle(), ip);
//            imp.updateImage();

//            // Before DCT
//            double [][] vals = new double[size][size];
//            for(int x=0; x<ip.getWidth(); x++){
//                for(int y=0; y<ip.getHeight(); y++){
//                    vals[x][y] = ip.getPixel(x, y);
//                }
//            }

            // DCT
            double [][] dctVals = applyDCT(vals, size, c);
            vals = null;
            // double total = 0;


            List<Double> arrayList = new ArrayList<Double>() ;
            for(int x=0; x<smallerSize; x++){
                for(int y=0; y<smallerSize; y++){
                    arrayList.add(dctVals[x][y]);
                }
            }


            //double avg = total / (double) ((smallerSize * smallerSize) - 1);

            // median value
            double median = median(arrayList);
            arrayList = null;

            String hexHash = "";
            String bit4 = "";
            for (int x=0; x<smallerSize; x++){
                for(int y=0; y<smallerSize; y++){
                    if ((x*smallerSize + y + 1) % 4 != 0 ) {
                        bit4 += (dctVals[x][y] > median ? "1" : "0");
                    } else{
                        bit4 += (dctVals[x][y] > median ? "1" : "0");
                        hexHash += Integer.toString(Integer.parseInt(bit4, 2), 16);
                        bit4 = "";
                    }
                }
            }
            result = new Text(hexHash);
        } catch(Exception e){
            System.out.println("[WARN] Error in Image Processing "+ main_img_url.toString());
            result = null;
        }
        return result;
    }

    private static double [][] applyDCT(double [][] f, int size, double [] c){
        int N = size;
        double [][] F = new double[N][N];
        for(int u=0; u<N; u++){
            for(int v=0; v<N; v++){
                double sum=0.0;
                for(int i=0; i<N; i++){
                    for(int j=0; j<N; j++){
                        sum += Math.cos((2*i+1)/(2.0*N) * u * Math.PI) * Math.cos((2*j+1)/(2.0*N)*v*Math.PI) * f[i][j];
                    }
                }
                sum *= (c[u] * c[v] /4.0);
                F[u][v] = sum;
            }
        }
        return F;
    }

    private static double median(List<Double> total) {
        double j = 0;
        // sort
        Collections.sort(total);
        int size = total.size();
        if(size % 2 == 1){
            j = total.get((size-1)/2);
        }else {
            j = (total.get(size/2-1) + total.get(size/2) + 0.0)/2;
        }
        return j;
    }

    private static String Hex2BinPHash(String hexPHash){
        String binPhash = "";
        for (int i=0; i<hexPHash.length(); i++){
            String tmp = "";
            tmp = Integer.toBinaryString(Integer.parseInt(new String(new char[] {hexPHash.charAt(i)}), 16));
            if(tmp.length()<4){
                tmp = "000"+ tmp;
                tmp = tmp.substring(tmp.length()-4);
            }
            binPhash += tmp;
        }
        return binPhash;
    }
}
