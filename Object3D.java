import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.ThreadLocalRandom;

public class Object3D
{
    Triangle[] triangles;

    Object3D(Triangle[] t)
    {
        this.triangles = t;
    }

    Object3D()
    {
        this.triangles = null;
    }

    public void draw(Graphics2D g2, Matrix4 x_r_transform, Matrix4 z_r_transform, Matrix4 map_projection, Vertex Camera, Vertex light_direction)
    {
        BufferedImage img = new BufferedImage(Renderer.WIDTH, Renderer.HEIGHT, BufferedImage.TYPE_INT_ARGB);

        double[][] zBuffer = new double[img.getHeight()][img.getWidth()];
        for (double[] row : zBuffer)
            Arrays.fill(row, Double.MAX_VALUE);

        for (Triangle t : this.triangles)
        {
            Path2D path = new Path2D.Double();
            g2.setColor(t.color);

            Vertex[] projected_points = new Vertex[3];
            Vertex[] rotated_points = new Vertex[3];

            // Rotate Points
            for (int i = 0; i < t.points.length; i++)
            {
                rotated_points[i] = z_r_transform.multiplyPoint(x_r_transform.multiplyPoint(t.points[i]));

                // Screen Offset
                rotated_points[i].z += 8;
            }

            // Calculate Normals and stuff
            Vertex normal = getNormal(rotated_points);

            // Normalise normal vector?
            double normal_length = Math.sqrt((normal.x * normal.x) + (normal.y * normal.y) + (normal.z * normal.z));
            normal.x /= normal_length;
            normal.y /= normal_length;
            normal.z /= normal_length;

            // Back Face Culling should go here (zbuffer won't be needed i think when it works)
            if (normal.z > 0)
                continue;

            // Project Points
            for (int i = 0; i < t.points.length; i++)
            {
                // Translate / Project
                Vertex projected_point = map_projection.multiplyPoint(rotated_points[i]);

                // Scale Point
                projected_point.x += 1;
                projected_point.y += 1;

                projected_point.x *= 0.5 * Renderer.WIDTH;
                projected_point.y *= 0.5 * Renderer.HEIGHT;

                // Draw Lines for Triangles
                if (i == 0)
                {
                    path.moveTo(projected_point.x, projected_point.y);
                } else {
                    path.lineTo(projected_point.x, projected_point.y);
                }

                // Store for projected points for rasterisation
                projected_points[i] = projected_point;
            }

            // Draw Triangle Lines
            path.closePath();
            g2.draw(path);

            // Rasterisation
            if (Renderer.DRAW_FACES)
            {
                Vertex v1 = projected_points[0];
                Vertex v2 = projected_points[1];
                Vertex v3 = projected_points[2];


                // Calculate Ranges
                int minX = (int) Math.max(0, Math.ceil(Math.min(v1.x, Math.min(v2.x, v3.x))));
                int maxX = (int) Math.min(img.getWidth() - 1, Math.floor(Math.max(v1.x, Math.max(v2.x, v3.x))));
                int minY = (int) Math.max(0, Math.ceil(Math.min(v1.y, Math.min(v2.y, v3.y))));
                int maxY = (int) Math.min(img.getHeight() - 1, Math.floor(Math.max(v1.y, Math.max(v2.y, v3.y))));

                double triangleArea = (v1.y - v3.y) * (v2.x - v3.x) + (v2.y - v3.y) * (v3.x - v1.x);

                for (int y = minY; y <= maxY; y++)
                {
                    for (int x = minX; x <= maxX; x++)
                    {

                        double b1 = ((y - v3.y) * (v2.x - v3.x) + (v2.y - v3.y) * (v3.x - x)) / triangleArea;
                        double b2 = ((y - v1.y) * (v3.x - v1.x) + (v3.y - v1.y) * (v1.x - x)) / triangleArea;
                        double b3 = ((y - v2.y) * (v1.x - v2.x) + (v1.y - v2.y) * (v2.x - x)) / triangleArea;

                        if (b1 >= 0 && b1 <= 1 && b2 >= 0 && b2 <= 1 && b3 >= 0 && b3 <= 1)
                        {

                            double depth = b1 * v1.z + b2 * v2.z + b3 * v3.z;

                            if (zBuffer[y][x] > depth)
                            {
                                // Now that we know we're coloring this pixel we do light calculations
                                light_direction.normalise();
                                double shade = light_direction.dot(normal);
//                                double shade = Math.abs(normal.z);

                                img.setRGB(x, y, t.getShade(shade).getRGB());
                                zBuffer[y][x] = depth;
                            }
                        }
                    }
                }
            }
        }

        if (Renderer.DRAW_FACES)
            g2.drawImage(img, 0, 0, null);
    }

    private static Vertex getNormal(Vertex[] rotated_points) {
        Vertex line1 = new Vertex(
                rotated_points[1].x - rotated_points[0].x,
                rotated_points[1].y - rotated_points[0].y,
                rotated_points[1].z - rotated_points[0].z
                );
        Vertex line2 = new Vertex(
                rotated_points[2].x - rotated_points[0].x,
                rotated_points[2].y - rotated_points[0].y,
                rotated_points[2].z - rotated_points[0].z
                );

        return line1.cross(line2);
    }

    public void loadFromOBJ(String file_path)
    {
        Scanner reader;
        File OBJ;
        ArrayList<Vertex> v = new ArrayList<>();
        ArrayList<Vertex> vn = new ArrayList<>();
        ArrayList<Triangle> t = new ArrayList<>();
        String line;
        String[] f_data_temp;
        int[] f_data = new int[3]; // Face Data
        int i;


        // Try to open file
        try {
            OBJ = new File(file_path);
            reader = new Scanner(OBJ);
        } catch (FileNotFoundException e) {
            System.out.println("ERROR: OBJ File \"" + file_path + "\" not found.");
            e.printStackTrace();
            return;
        }

        // Read data
        while (reader.hasNextLine())
        {
            line = reader.nextLine();

            String[] data = line.split(" ");

            switch (data[0])
            {
                case "v":
                    v.add(new Vertex(data[1], data[2], data[3]));
                    break;
                case "vn":
                    vn.add(new Vertex(data[1], data[2], data[3]));
                    break;
                case "f":
                    // Sample face data : "f 5//1 3//1 1//1" where each thing is v//normal_v

                    for (i = 1; i < 4; i++)
                    {
                        f_data_temp = data[i].split("//");
                        f_data[i-1] = Integer.parseInt(f_data_temp[0]) - 1;
                    }

                    t.add(new Triangle(v.get(f_data[0]), v.get(f_data[1]), v.get(f_data[2]), Color.WHITE));
                    break;
            }

        }

        this.triangles = new Triangle[t.size()];
        this.triangles = t.toArray(this.triangles);
    }

    public void randomRGB()
    {
        for (Triangle t : triangles)
        {
            int r = ThreadLocalRandom.current().nextInt(0, 256);
            int g = ThreadLocalRandom.current().nextInt(0, 256);
            int b = ThreadLocalRandom.current().nextInt(0, 256);
            t.color = new Color(r, g, b);
        }
    }

    public void print()
    {
        for (Triangle t : this.triangles)
        {
            for (Vertex p : t.points)
            {
                p.print();
            }
            System.out.println();
        }
    }
}
