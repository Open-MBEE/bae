# whether to print debug information
debug = False

#handy python things
import time,sys,traceback,re

#some MD utils. 
from com.nomagic.magicdraw.core import * #application, project...
from com.nomagic.magicdraw.core import Application #this seems to want its own import.

from javax.swing import JOptionPane

import MPUtils
reload (MPUtils)

global gl
gl = Application.getInstance().getGUILog()

def logInfo(xLabel, x):
    gl.log( str(xLabel) + "=" + str(x)) #+ " : available functions = " + str(dir(x)))

def getSelectedElementsInDiagram():
    project = Application.getInstance().getProject()
    diagram = None
    selectedList = None
    if project != None: diagram = project.getActiveDiagram()
    if diagram != None: selectedList = diagram.getSelected()
    if selectedList != None: selectedElements = [ x.getElement() for x in selectedList ] 
    return selectedList

def getSelectedElementsInBrowser(complain):
    elements = []
    tree = Application.getInstance().getMainFrame().getBrowser().getActiveTree()
    if tree == None:
        gl.log("No active tree!")
        return elements
    if debug: logInfo("tree", tree)
    nodes = tree.getSelectedNodes()
    if debug: logInfo("nodes", nodes)
    if nodes != None and len(nodes) > 0:
        for selectedNode in nodes:
            if selectedNode != None:
                if debug: logInfo("selected node", selectedNode)
                selected = selectedNode.getUserObject()
                if selected != None:
                    elements.append(selected)
                    if debug: logInfo("selected", selected)
                elif complain:
                    gl.log("selected = None")
            elif complain:
                gl.log("selectedNode = None")
    elif complain:
        gl.log("No selected nodes!")
    return elements

def run(s):
    gl.log(" ")
    gl.log(" --- whatsMyId ---")
    if debug: logInfo("s", s)
    #get the user's selection - the element(s) that should be top level and 
    #contain (recursively) all other systems/behaviors you wish to reason about.
    selectedElementSet = set()
    for e in getSelectedElementsInDiagram(): selectedElementSet.add(e)
    m = {"diagram":selectedElementSet, "browser":set()}
    for e in getSelectedElementsInBrowser(False):
        if e not in selectedElementSet:
            m["browser"].add(e)
            selectedElementSet.add(e)
    for mItem in m.items():
        if mItem[1] != None and len(mItem[1]) > 0:
            gl.log("selected in " + mItem[0])
        for selected in mItem[1]:
            gl.log("  name = " + selected.name + ", id = " + str(selected.getID()))
    if len(selectedElementSet) <= 0:
        gl.log("No elements selected!")
        gl.log(" ")
    return

