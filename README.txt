Analysis Engine README.txt 2013-22-11

Original developers: Bradley.J.Clement@jpl.nasa.gov, Maddalena.M.Jackson@jpl.nasa.gov

See CyberSecSG-AESetupInstructions-17Sep13-0508PM-4.pdf

Setting up MagicDraw (MD) Plugins

<TODO -- Need to complete and integrate instructions on setting up and running MD plugins.>

1. run 'ant copyAePlugin' or copy/soft link the src/gov/nasa/jpl/ae/magicdrawPlugin/AE folder and optionally add AE.jar to ${MagicDrawInstallation}/plugins/gov.nasa.jpl.ae 

2. Edit the files inside to make sure paths are setup properly: main.py, plugin.xml, and script.xml.

3. Select or right-click a class (or maybe package), whose behavior starts a scenario through activity diagrams, in the MD containment tree and choose
   a. LADWP->ExportForAnalysisEngine_v2.  This generates the event xml and prints the name and location of the file in MD's message window.
   b. LADWP->AE if running JRebel.  This will likely not work, but if it does, it should do steps 4, 5, and 6 for you, and you can go to step 7.

4. Follow the directions in CyberSecSG-AESetupInstructions-17Sep13-0508PM-4.pdf to translate the xml file in to a Java model in some subdirectory of src, that probably matches the name of the xml file.

5. Follow the directions in CyberSecSG-AESetupInstructions-17Sep13-0508PM-4.pdf to generate simulation output.

6. From MD, run LADWP->MagicDrawAnimator2 and specify the text file with the simulation output (you may find a file in the AE/CS workspace folder named best_snapshot.*.txt).

7. You should
   a. be prompted to start an animated simulation in the MD diagrams and 
   b. then maybe also see an animated plot of any variables set up for plotting.
 