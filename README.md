# Zettascale Workload Generator

## Functionality

This tool uses an existing SWF workload trace (eascale Frontier 2025 workload trace) and **scales it up** to mimic future zettascale-like workloads.

**LOGIC**: The parser reads jobs and increases the number of jobs and their requirements to represent the increased workload density expected on a zettascale-class system. First, is doubles the resource request of each Frontier job and then it replicates it sixfold, with each replica assigned a randomized submission-time jitter of 0-59 seconds to increase job arrival density while preserving the statistical characteristics of the original workload. 

Together, this causes 2*6 = 12x higher node demand, which roughly covers the zettascale's increase in number of nodes.

All generated job entries are subsequently merged into a unified stream and sorted chronologically according to their adjusted arrival times. Finally, the generator extracts ten contiguous trace samples from this stream, producing ten different traces, each capturing a week-long job submission stream.

### Main Features

* The generator produces SWF-like workload format. 
* The data set used for input in SWF format is available at https://jsspp.org/workload/ 
* Sample data set is provided within the distribution (see `./frontier-2025.txt`).
* By default, the generator produces 10 different samples based on the original workload trace
* Beside a *job trace*, the generator also generates files describing *users*, *groups* and *machines* (as required by AleaNG job scheduling simulator)


## Compiling and Running 
Zettascale workload generator is distributed as **Netbeans project**, but can be easily compiled using just standard Java (JDK) package commands. 

### Compilation on Linux systems
On Linux-like systems, extract the downloaded zip folder and run the following commands:

```bash
mkdir -p out
javac -d out $(find src -name "*.java")
```
The `mkdir -p out` will create `./out` folder for the compiled bytecode. Next, `javac...` command will compile all java classes.

### Compilation on Windows systems
On Windows-like systems, extract the downloaded zip folder and run following commands (in **PowerShell**: `powershell.exe`):

```bash
mkdir out
$files = Get-ChildItem -Recurse -Filter *.java src | ForEach-Object { $_.FullName }
javac -d out $files
```

It is important to **run these commands in PowerShell**, since the standard `cmd.exe` uses a different syntax. 

### Running AleaNG on Linux systems
On Linux-like systems, run:

```bash
java -cp ./out zettascaleworkloadgenerator.ZettaScaleWorkloadGenerator
```

### Running AleaNG on Windows systems
To run your program, you just need to call java with the correct classpath and your main class name (`ZettaScaleWorkloadGenerator`).

```cmd
java -cp .\out\ zettascaleworkloadgenerator.ZettaScaleWorkloadGenerator
```

##### Software licence:

This software is provided as is, free of charge under the terms of the LGPL licence. 

##### Important

When using the generator AND the Frontier 2025 workload trace, please acknowledge the paper that introduced these traces.