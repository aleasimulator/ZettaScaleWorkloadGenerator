/**
 * Zettascale workload generator.
 *
 * @author Dalibor Klusáček
 * @since 2026
 * 
 */

package zettascaleworkloadgenerator;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * This class generates SWF files for AleaNG job scheduling simulator modelling future zettascale-like
 * HPC workloads. The input is the exascale Frontier-2025 SWF trace which is upscaled.
 * Note: 6x times more jobs with double their original node requirements.
 */
public class ZettaScaleWorkloadGenerator {

    private static final int CLONE_FACTOR = 6; // how many jobs to generate from one original job
    private static final int NODE_MULTIPLIER = 2; // how much more nodes shall these hjobs require
    private static final int SUBMIT_GAP_MAX = 60; // a timeframe for these jobs to arrive in (in seconds)
    private static final int MAX_JOBS = 500000;
    private static final int SAMPLES = 10; // how many samples to take from the original workload trace
    private static final int NODES = 128000; // number of nodes in the system
    private static final int CORES_PER_NODE = 512; // Cores per node
    private static final String inputFile = "frontier-2025.txt";
    private static final String outputFile = "frontierZetta-2025";
    private static final int TRACE_DURATION = 604800; // the duration of the generated trace (1 week in seconds)

    /**
     * Class representing one job (used for job sorting via their submit time)
     */
    
    static class JobEntry {

        long submitTime;
        String dataLine; // rest of job description

        JobEntry(long submitTime, String dataLine) {
            this.submitTime = submitTime;
            this.dataLine = dataLine;
        }
    }
    
    /**
     * Main parser and generator code.
     * @param args (not used)
     */

    public static void main(String[] args) {

        List<JobEntry> allJobs = new ArrayList<>(1000000); // Prealloc for performance
        ArrayList<Integer> user_ids = new ArrayList<Integer>();
        ArrayList<Integer> group_ids = new ArrayList<Integer>();
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
                int user_id = Integer.parseInt(parts[11]);
                int group_id = Integer.parseInt(parts[12]);
                if (!user_ids.contains(user_id)) {
                    user_ids.add(user_id);
                }
                if (!group_ids.contains(group_id)) {
                    group_ids.add(group_id);
                }
                String properties = parts[20];

                // clone each job to increase job density
                for (int i = 0; i < CLONE_FACTOR; i++) {
                    long jitter = (i == 0) ? 0 : new Random(1234).nextInt(SUBMIT_GAP_MAX);
                    long newSubmitTime = originalSubmitTime + jitter;

                    // Scale job requirements
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

        System.out.println("Sorting " + allJobs.size() + " jobs by arrival time...");
        // Sort (TimSort) by submit time
        allJobs.sort(Comparator.comparingLong(j -> j.submitTime));

        int job_pointer = 0;
        long last_start = -1;
        long last_arrival = 0;
        long last_diff_time = 0;

        // generate job files 
        for (int i = 0; i < SAMPLES; i++) {

            System.out.println(i + "th sample trace. Writing SWF file...");
            int newJobId = 1;
            int jobs_in_sample = 0;
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile + "-" + i + ".swf"))) {
                // Job SWF header
                for (String h : header) {
                    if (h.contains("; number of jobs: ")) {
                        h = "; number of jobs: " + Math.min(MAX_JOBS, allJobs.size());
                    }
                    bw.write(h);
                    bw.newLine();
                }

                // write sorted jobs with new IDs
                while ((last_arrival - last_start) < TRACE_DURATION) {
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
                    if ((last_arrival - last_start) >= TRACE_DURATION) {
                        //reset for next round so that while cycle will start
                        last_start = last_arrival;
                        break;
                    }

                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            System.out.println(i + "th sample, done. Total jobs: " + (newJobId - 1));
            updateJobCountInFile(outputFile + "-" + i + ".swf", MAX_JOBS, jobs_in_sample);

            // write users and groups
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile + "-" + i + ".swf.users"))) {
                // user header
                bw.write(";user_id	user_name	shares	CPU_quota");
                bw.newLine();
                bw.write(";-----------------------------------------------------------------------------");
                bw.newLine();

                for (Integer user : user_ids) {
                    String line = user + "\tuser_" + user + "\t1\t" + (CORES_PER_NODE * NODES);
                    bw.write(line);
                    bw.newLine();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile + "-" + i + ".swf.groups"))) {
                // group header
                bw.write(";group_id	group_name	shares	CPU_quota");
                bw.newLine();
                bw.write(";-----------------------------------------------------------------------------");
                bw.newLine();

                for (Integer group : group_ids) {
                    String line = group + "\tgroup_" + group + "\t1\t" + (CORES_PER_NODE * NODES);
                    bw.write(line);
                    bw.newLine();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile + "-" + i + ".swf.machines"))) {
                // machine header                
                bw.write(";cl_id	name		nodes	CPUs	speed	GB_RAM	properties	GPUs");
                bw.newLine();
                bw.write(";-----------------------------------------------------------------------------");
                bw.newLine();
                String line = "0	frontierZETTA 	 " + NODES + " 	 " + CORES_PER_NODE + " 	 1 	 256 	 zettascale 	 16";
                bw.write(line);
                bw.newLine();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
    
    /**
     * Auxiliary method for scaling-up original exascale-based job
     * @param prop properties of the riginal job
     * @param multiplier scale-up factor 
     * @return 
     */

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
            segments[0] = "zettascale";
            return String.join(":", segments);
        } catch (Exception e) {
            return prop;
        }
    }

    private static void updateJobCountInFile(String file, int max_size, int jobs) {
        Path originalFile = Paths.get(file);
        Path tempFile = Paths.get(file + "_temp.txt");

        // update job number once they are printed to file
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
            return; 
        }

        // Replace original job trace with the temp file
        try {
            Files.move(tempFile, originalFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
