/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package zettascaleworkloadgenerator;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class ZettaScaleWorkloadGenerator {

    private static final int CLONE_FACTOR = 6;
    private static final int NODE_MULTIPLIER = 2;
    private static final int SUBMIT_GAP_MAX = 60;
    private static final int MAX_JOBS = 500000;

    // Pomocná třída pro efektivní uložení v paměti před seřazením
    static class JobEntry {

        long submitTime;
        String dataLine; // Ostatní pole uložíme jako jeden řetězec

        JobEntry(long submitTime, String dataLine) {
            this.submitTime = submitTime;
            this.dataLine = dataLine;
        }
    }

    public static void main(String[] args) {
        String inputFile = "frontier-2025.txt";
        String outputFile = "frontierZetta-2025";

        List<JobEntry> allJobs = new ArrayList<>(1000000); // Předalokace pro výkon
        List<String> header = new ArrayList<>();

        System.out.println("Loading and scaling jobs...");

        try (BufferedReader br = new BufferedReader(new FileReader(inputFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith(";")) {
                    header.add(line);
                    continue;
                }

                String[] parts = line.trim().split("\\s+");
                if (parts.length < 21) {
                    continue;
                }

                long originalSubmitTime = Long.parseLong(parts[1]);
                String properties = parts[20];

                for (int i = 0; i < CLONE_FACTOR; i++) {
                    long jitter = (i == 0) ? 0 : new Random(1234).nextInt(SUBMIT_GAP_MAX);
                    long newSubmitTime = originalSubmitTime + jitter;

                    // Upravíme zbytek polí (kromě ID a submitTime, ty vyřešíme při zápisu)
                    int scaledNodes = Integer.parseInt(parts[7]) * NODE_MULTIPLIER;
                    parts[4] = String.valueOf(scaledNodes);
                    parts[7] = String.valueOf(scaledNodes);
                    parts[20] = scaleProperties(properties, NODE_MULTIPLIER);

                    // Sestavíme zbytek řádku (bez prvních dvou polí)
                    StringBuilder sb = new StringBuilder();
                    for (int j = 2; j < parts.length; j++) {
                        sb.append(parts[j]).append("\t");
                    }

                    allJobs.add(new JobEntry(newSubmitTime, sb.toString().trim()));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        System.out.println("Sorting "+allJobs.size()+" jobs by arrival time...");
        // Stabilní třídění (TimSort) podle času
        allJobs.sort(Comparator.comparingLong(j -> j.submitTime));

        /*System.out.println("Zapisuji seřazený SWF soubor...");
        int newJobId = 1;
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile))) {
            // Zápis hlavičky
            for (String h : header) {
                if (h.contains("; number of jobs: ")) {
                    h = "; number of jobs: " + Math.min(MAX_JOBS, allJobs.size());
                }
                bw.write(h);
                bw.newLine();
            }

            // Zápis seřazených dat s novými ID
            for (JobEntry job : allJobs) {
                if (newJobId > MAX_JOBS) {
                    break;
                }
                bw.write(newJobId + "\t" + job.submitTime + "\t" + job.dataLine);
                bw.newLine();
                newJobId++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Hotovo. Celkem úloh: " + (newJobId - 1));
         */
        int samples = 10;
        int job_pointer = 0;
        long last_start = -1;
        long last_arrival = 0;
        long last_diff_time = 0;

        for (int i = 0; i < samples; i++) {

            System.out.println(i + "th sample trace. Writing SWF file...");
            int newJobId = 1;
            int jobs_in_sample = 0;
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile + "-" + i + ".swf"))) {
                // Zápis hlavičky
                for (String h : header) {
                    if (h.contains("; number of jobs: ")) {
                        h = "; number of jobs: " + Math.min(MAX_JOBS, allJobs.size());
                    }
                    bw.write(h);
                    bw.newLine();
                }

                // Zápis seřazených dat s novými ID
                
                while ((last_arrival - last_start) < 604800) {
                    JobEntry job = allJobs.get(job_pointer);
                    last_arrival = job.submitTime;
                    if (newJobId == 1) {
                        last_start = job.submitTime;
                        last_diff_time = job.submitTime;
                    }
                    if (newJobId > MAX_JOBS) {
                        break;
                    }
                    bw.write(newJobId + "\t" + (job.submitTime - last_diff_time) + "\t" + job.dataLine);
                    bw.newLine();
                    jobs_in_sample++;
                    newJobId++;
                    job_pointer++;
                    if ((last_arrival - last_start) >= 604800) {
                        //reset for next round so that while cycle will start
                        last_start = last_arrival;
                        break;
                    }

                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            System.out.println(i + "th sample, done. Total jons: " + (newJobId - 1));
            updateJobCountInFile(outputFile + "-" + i + ".swf", MAX_JOBS, jobs_in_sample);
            
        }

    }

    private static String scaleProperties(String prop, int multiplier) {
        try {
            String[] segments = prop.split(":");
            if (segments.length < 3) {
                return prop;
            }
            String[] geometry = segments[2].split("x");
            if (geometry.length == 2) {
                int nodes = Integer.parseInt(geometry[0]);
                int coresPerNode = Integer.parseInt(geometry[1]);
                coresPerNode = 512;
                segments[2] = (nodes * multiplier) + "x" + coresPerNode;
            }
            segments[0] = "zetascale";
            return String.join(":", segments);
        } catch (Exception e) {
            return prop;
        }
    }

    private static void updateJobCountInFile(String file, int max_size, int jobs) {
        Path originalFile = Paths.get(file);
        Path tempFile = Paths.get(file+"_temp.txt");

        // 1. Čtení originálu a zápis upravených řádků do dočasného souboru
        try (BufferedReader br = Files.newBufferedReader(originalFile); BufferedWriter bw = Files.newBufferedWriter(tempFile)) {

            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("; number of jobs: ")) {
                    line = "; number of jobs: " + Math.min(jobs, max_size);
                }
                bw.write(line);
                bw.newLine();
            }

        } catch (IOException e) {
            e.printStackTrace();
            return; // Ukončit v případě chyby čtení/zápisu
        }

        // 2. Nahrazení původního souboru dočasným souborem
        try {
            Files.move(tempFile, originalFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
