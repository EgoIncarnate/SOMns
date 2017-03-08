/* jshint -W097 */
"use strict";

import {Controller} from "./controller";
import {Source, Method, StackFrame, SourceCoordinate, StackTraceResponse,
  TaggedSourceCoordinate, Scope, getSectionId, Variable, Activity } from "./messages";
import {Breakpoint, MessageBreakpoint, LineBreakpoint} from "./breakpoints";

declare var ctrl: Controller;

const ACT_ID_PREFIX = "a";

function getActivityId(id: number) {
  return ACT_ID_PREFIX + id;
}

export function getLineId(line: number, sourceId: string) {
  return sourceId + "ln" + line;
}

function getSectionIdForActivity(ssId: string, activityId: number) {
  return getActivityId(activityId) + ssId;
}

function getSourceIdForActivity(sId: string, activityId: number) {
  return getActivityId(activityId) + sId;
}

export function getSourceIdFrom(actAndSourceId: string) {
  const i = actAndSourceId.indexOf("s");
  console.assert(i > 0);
  console.assert(actAndSourceId.indexOf(":") === -1);
  return actAndSourceId.substr(i);
}

export function getSectionIdFrom(actAndSourceId: string) {
  const i = actAndSourceId.indexOf("s");
  console.assert(i > 0);
  return actAndSourceId.substr(i);
}

export function getSourceIdFromSection(sectionId: string) {
  const i = sectionId.indexOf(":");
  console.assert(i > 1);
  return sectionId.substr(0, i);
}

export function getActivityIdFromView(actId: string) {
  return parseInt(actId.substr(ACT_ID_PREFIX.length));
}

function splitAndKeepNewlineAsEmptyString(str) {
  let result = new Array();
  let line = new Array();

  for (let i = 0; i < str.length; i++) {
    line.push(str[i]);
    if (str[i] === "\n") {
      line.pop();
      line.push("");
      result.push(line);
      line = new Array();
    }
  }
  return result;
}

function sourceToArray(source: string): string[][] {
  let lines = splitAndKeepNewlineAsEmptyString(source);
  let arr = new Array(lines.length + 1);  // +1 is to work around files not ending in newline

  for (let i in lines) {
    let line = lines[i];
    arr[i] = new Array(line.length);
    for (let j = 0; j < line.length; j += 1) {
      arr[i][j] = line[j];
    }
  }
  arr[lines.length] = [""]; // make sure the +1 line has an array with an empty string
  return arr;
}

function methodDeclIdToString(sectionId: string, idx: number, activityId: number) {
  return getActivityId(activityId) + "m-" + sectionId + "-" + idx;
}

function methodDeclIdToObj(id: string) {
  console.assert(id.indexOf("-") !== -1);
  const idComponents = id.split("-");
  let arr = idComponents[1].split(":");
  return {
    sourceId:    arr[0],
    startLine:   parseInt(arr[1]),
    startColumn: parseInt(arr[2]),
    charLength:  parseInt(arr[3]),
    idx:         parseInt(idComponents[2])
  };
}

abstract class SectionMarker {
  public type: any;

  constructor(type: any) {
    this.type = type;
  }

  abstract length(): number;
}

class Begin extends SectionMarker {
  private section: TaggedSourceCoordinate;
  private sectionId: string;
  private activityId: number;

  constructor(section: TaggedSourceCoordinate, sectionId: string,
      activityId: number) {
    super(Begin);
    this.sectionId  = sectionId;
    this.activityId = activityId;
    this.section = section;
    this.type    = Begin;
  }

  toString() {
    return '<span id="' + getSectionIdForActivity(this.sectionId, this.activityId)
         + '" class="' + this.section.tags.join(" ") + " " + this.sectionId + '">';
  }

  length() {
    return this.section.charLength;
  }
}

class BeginMethodDef extends SectionMarker {
  private method:   Method;
  private sourceId: string;
  private activityId: number;
  private i:        number;
  private defPart:  SourceCoordinate;

  constructor(method: Method, sourceId: string, i: number, activityId: number,
      defPart: SourceCoordinate) {
    super(Begin);
    this.method   = method;
    this.sourceId = sourceId;
    this.i        = i;
    this.defPart  = defPart;
    this.activityId = activityId;
  }

  length() {
    return this.defPart.charLength;
  }

  toString() {
    const tags = "MethodDeclaration",
      sectionId = getSectionId(this.sourceId, this.method.sourceSection),
      id = methodDeclIdToString(sectionId, this.i, this.activityId);
    return '<span id="' + id + '" class="' + tags + " " + sectionId + '">';
  }
}

class End extends SectionMarker {
  private section: SourceCoordinate;
  private len:     number;

constructor(section: SourceCoordinate, length: number) {
    super(End);
    this.section = section;
    this.len     = length;
  }

  toString() {
    return "</span>";
  }

  length() {
    return this.len;
  }
}

class Annotation {
  private char: string;
  private before: SectionMarker[];
  private after:  SectionMarker[];

  constructor(char: string) {
    this.char   = char;
    this.before = [];
    this.after  = [];
  }

  toString() {
    this.before.sort(function (a, b) {
      if (a.type !== b.type) {
        if (a.type === Begin) {
          return -1;
        } else {
          return 1;
        }
      }

      if (a.length() === b.length()) {
        return 0;
      }

      if (a.length() < b.length()) {
        return (a.type === Begin) ? 1 : -1;
      } else {
        return (a.type === Begin) ? -1 : 1;
      }
    });

    let result = this.before.join("");
    result += this.char;
    result += this.after.join("");
    return result;
  }
}

function arrayToString(arr: any[][]) {
  let result = "";

  for (let line of arr) {
    for (let c of line) {
      result += c.toString();
    }
    result += "\n";
  }
  return result;
}

function nodeFromTemplate(tplId: string) {
  const tpl = document.getElementById(tplId),
    result = <Element> tpl.cloneNode(true);
  result.removeAttribute("id");
  return result;
}

function createLineNumbers(cnt: number, sourceId: string) {
  let result = "<span class='ln ln1' onclick='ctrl.onToggleLineBreakpoint(1, this);'>1</span>";
  for (let i = 2; i <= cnt; i += 1) {
    result = result + "\n<span class='ln " + getLineId(i, sourceId) +
      "' onclick='ctrl.onToggleLineBreakpoint(" + i + ", this);'>" + i + "</span>";
  }
  return result;
}

/**
 * Arguments and results are 1-based.
 * Computation is zero-based.
 */
function ensureItIsAnnotation(arr: any[][], line: number, column: number) {
  let l = line - 1,
    c = column - 1;

  if (!(arr[l][c] instanceof Annotation)) {
    console.assert(typeof arr[l][c] === "string");
    arr[l][c] = new Annotation(arr[l][c]);
  }
  return arr[l][c];
}

/**
 * Determine line and column for `length` elements from given start location.
 *
 * Arguments and results are 1-based.
 * Computation is zero-based.
 */
function getCoord(arr: any[][], startLine: number, startColumn: number, length: number) {
  let remaining = length,
    line   = startLine - 1,
    column = startColumn - 1;

  while (remaining > 0) {
    while (column < arr[line].length && remaining > 0) {
      column    += 1;
      remaining -= 1;
    }
    if (column === arr[line].length) {
      line      += 1;
      column    =  0;
      remaining -= 1; // the newline character
    }
  }
  return {line: line + 1, column: column + 1};
}

function annotateArray(arr: any[][], sourceId: string, activityId: number,
    sections: TaggedSourceCoordinate[], methods: Method[]) {
  for (let s of sections) {
    let start = ensureItIsAnnotation(arr, s.startLine, s.startColumn),
        coord = getCoord(arr, s.startLine, s.startColumn, s.charLength),
          end = ensureItIsAnnotation(arr, coord.line, coord.column),
    sectionId = getSectionId(sourceId, s);

    start.before.push(new Begin(s, sectionId, activityId));
    end.before.push(new End(s, s.charLength));
  }

  // adding method definitions
  for (let meth of methods) {
    for (let i in meth.definition) {
      let defPart = meth.definition[i],
        start = ensureItIsAnnotation(arr, defPart.startLine, defPart.startColumn),
        coord = getCoord(arr, defPart.startLine, defPart.startColumn, defPart.charLength),
        end   = ensureItIsAnnotation(arr, coord.line, coord.column);

      start.before.push(new BeginMethodDef(meth, sourceId, parseInt(i),
                                           activityId, defPart));
      end.before.push(new End(meth.sourceSection, defPart.charLength));
    }
  }
}

function enableEventualSendClicks(fileNode) {
  const sendOperator = fileNode.find(".EventualMessageSend");
  sendOperator.attr({
    "data-toggle"    : "popover",
    "data-trigger"   : "click hover",
    "title"          : "Breakpoints",
    "data-html"      : "true",
    "data-animation" : "false",
    "data-placement" : "top"
  });

  sendOperator.attr("data-content", function() {
    let content = nodeFromTemplate("actor-bp-menu");
    // capture the source section id, and store it on the buttons
    $(content).find("button").attr("data-ss-id", getSectionIdFrom(this.id));
    return $(content).html();
  });
  sendOperator.popover();

  $(document).on("click", ".bp-rcv-msg", function (e) {
    e.stopImmediatePropagation();
    ctrl.onToggleSendBreakpoint(e.currentTarget.attributes["data-ss-id"].value, "MessageReceiverBreakpoint");
  });

  $(document).on("click", ".bp-send-msg", function (e) {
    e.stopImmediatePropagation();
    ctrl.onToggleSendBreakpoint(e.currentTarget.attributes["data-ss-id"].value, "MessageSenderBreakpoint");
  });

  $(document).on("click", ".bp-rcv-prom", function (e) {
    e.stopImmediatePropagation();
    ctrl.onTogglePromiseBreakpoint(e.currentTarget.attributes["data-ss-id"].value, "PromiseResolutionBreakpoint");
  });

  $(document).on("click", ".bp-send-prom", function (e) {
    e.stopImmediatePropagation();
    ctrl.onTogglePromiseBreakpoint(e.currentTarget.attributes["data-ss-id"].value, "PromiseResolverBreakpoint");
  });
}

function enableChannelClicks(fileNode) {
  constructChannelBpMenu(fileNode, ".ChannelRead",  "channel-read-bp-menu");
  constructChannelBpMenu(fileNode, ".ChannelWrite", "channel-write-bp-menu");
}

function constructChannelBpMenu(fileNode, tag: string, tpl: string) {
  const sendOperator = fileNode.find(tag);
  sendOperator.attr({
    "data-toggle"    : "popover",
    "data-trigger"   : "click hover",
    "title"          : "Breakpoints",
    "data-html"      : "true",
    "data-animation" : "false",
    "data-placement" : "top"
  });

  sendOperator.attr("data-content", function() {
    let content = nodeFromTemplate(tpl);
    // capture the source section id, and store it on the buttons
    $(content).find("button").attr("data-ss-id", getSectionIdFrom(this.id));
    return $(content).html();
  });
  sendOperator.popover();

  $(document).on("click", ".bp-before", function (e) {
    e.stopImmediatePropagation();
    ctrl.onToggleSendBreakpoint(e.currentTarget.attributes["data-ss-id"].value, "MessageSenderBreakpoint");
  });

  $(document).on("click", ".bp-after", function (e) {
    e.stopImmediatePropagation();
    ctrl.onToggleSendBreakpoint(e.currentTarget.attributes["data-ss-id"].value, "ChannelOppositeBreakpoint");
  });
}

function enableMethodBreakpointHover(fileNode) {
  let methDecls = fileNode.find(".MethodDeclaration");
  methDecls.attr({
    "data-toggle"   : "popover",
    "data-trigger"  : "click hover",
    "title"         : "Breakpoints",
    "animation"     : "false",
    "data-html"     : "true",
    "data-animation": "false",
    "data-placement": "top" });

  methDecls.attr("data-content", function () {
    let idObj = methodDeclIdToObj(this.id);
    let content = nodeFromTemplate("method-breakpoints");
    $(content).find("button").attr("data-ss-id", getSectionId(idObj.sourceId, idObj));
    return $(content).html();
  });

  methDecls.popover();

  $(document).on("click", ".bp-async-rcv", function (e) {
    e.stopImmediatePropagation();
    ctrl.onToggleMethodAsyncRcvBreakpoint(e.currentTarget.attributes["data-ss-id"].value);
  });
}

/**
 * The HTML View, which realizes all access to the DOM for displaying
 * data and reacting to events.
 */
export class View {
  constructor() { }

  onConnect() {
    $("#dbg-connect-btn").html("Connected");
  }

  onClose() {
    $("#dbg-connect-btn").html("Reconnect");
  }

  /**
   * @returns true, if new source is displayed
   */
  public displaySource(activityId: number, source: Source, sourceId: string): boolean {
    const actId = getActivityId(activityId);
    const container = $("#" + actId + " .activity-sources-list");

    // we mark the tab header as well as the tab content with a class
    // that contains the source id
    let sourceElem = container.find("li." + sourceId);

    if (sourceElem.length !== 0) {
      const existingAElem = sourceElem.find("a");
      // we got already something with the source id
      // does it have the same name?
      if (existingAElem.get(0).innerHTML !== source.name) {
        // clear and remove tab header and tab content
        sourceElem.html("");
        sourceElem.remove();
      } else {
        return false; // source is already there, so, I think, we don't need to update it
      }
    }

    // show the source
    const annotationArray = sourceToArray(source.sourceText);

    // TODO: think this still need to be updated for multiple activities
    annotateArray(annotationArray, sourceId, activityId, source.sections,
      source.methods);

    const tabListEntry = nodeFromTemplate("tab-list-entry");
    $(tabListEntry).addClass(sourceId);

    // create the tab "header/handle"
    const aElem = $(tabListEntry).find("a");
    const sourcePaneId = getSourceIdForActivity(sourceId, activityId);
    aElem.attr("href", "#" + sourcePaneId);
    aElem.text(source.name);
    container.append(tabListEntry);

    // create tab pane
    const newFileElement = nodeFromTemplate("file");
    newFileElement.setAttribute("id", sourcePaneId);
    newFileElement.getElementsByClassName("line-numbers")[0].innerHTML = createLineNumbers(annotationArray.length, sourceId);
    const fileNode = newFileElement.getElementsByClassName("source-file")[0];
    $(fileNode).addClass(sourceId);
    fileNode.innerHTML = arrayToString(annotationArray);

    // enable clicking on EventualSendNodes
    enableEventualSendClicks($(fileNode));
    enableChannelClicks($(fileNode));
    enableMethodBreakpointHover($(fileNode));

    const sourceContainer = $("#" + actId + " .activity-source");
    sourceContainer.append(newFileElement);

    aElem.tab("show");
    return true;
  }

  public displayActivity(name: string, id: number) {
    const act = nodeFromTemplate("activity-tpl");
    $(act).find(".activity-name").html(name);
    const actId = getActivityId(id);
    act.id = actId;
    $(act).find("button").attr("data-actId", actId);

    const codeView = document.getElementById("code-views");
    codeView.appendChild(act);
  }

  public addActivities(activities: Activity[]) {
    for (const act of activities) {
      this.displayActivity(act.name, act.id);
    }
  }

  public displayProgramArguments(args: String[]) {
    $("#program-args").text(args.join(" "));
  }

  private getScopeId(varRef: number) {
    return "scope-" + varRef;
  }

  public displayScope(varRef: number, s: Scope) {
    const list = $("#" + this.getScopeId(varRef)).find("tbody");
    const entry = nodeFromTemplate("scope-head-tpl");
    entry.id = this.getScopeId(s.variablesReference);
    let t = $(entry).find("th");
    t.html(s.name);
    list.append(entry);
  }

  private createVarElement(name: string, value: string, varRef: number): Element {
    const entry = nodeFromTemplate("frame-state-tpl");
    entry.id = this.getScopeId(varRef);
    let t = $(entry).find("td");
    t.get(0).innerHTML = name;
    t = $(entry).find("td");
    t.get(1).innerHTML = value;
    return entry;
  }

  public displayVariables(varRef: number, vars: Variable[]) {
    const scopeEntry = document.getElementById(this.getScopeId(varRef));

    for (const v of vars) {
      scopeEntry.insertAdjacentElement(
        "afterend",
        this.createVarElement(v.name, v.value, v.variablesReference));
    }
  }

  private getFrameId(frameId: number) {
    return "frame-" + frameId;
  }

  private showFrame(frame: StackFrame, active: boolean, list: JQuery) {
    const fileNameStart = frame.sourceUri.lastIndexOf("/") + 1;
    const fileName = frame.sourceUri.substr(fileNameStart);
    const location = fileName + ":" + frame.line + ":" + frame.column;

    const entry = nodeFromTemplate("stack-trace-elem-tpl");
    entry.id = this.getFrameId(frame.id);

    if (active) {
      $(entry).addClass("active");
    }

    const name = $(entry).find(".trace-method-name");
    name.html(frame.name);
    const loc = $(entry).find(".trace-location");
    loc.html(location);

    list.append(entry);
  }

  public displayStackTrace(sourceId: string, data: StackTraceResponse, requestedId: number) {
    const act = $("#" + getActivityId(data.activityId));
    const list = act.find(".activity-stack");
    list.html(""); // rest view

    for (let i = 0; i < data.stackFrames.length; i++) {
      this.showFrame(data.stackFrames[i],
        data.stackFrames[i].id === requestedId, list);
      console.assert(data.stackFrames[i].id !== requestedId || i === 0, "We expect that the first frame is displayed.");
    }
    const scopes = act.find(".activity-scopes");
    scopes.attr("id", this.getScopeId(requestedId));
    scopes.find("tbody").html(""); // rest view

    this.highlightProgramPosition(sourceId, data.activityId, data.stackFrames[0]);
  }

  private highlightProgramPosition(sourceId: string, activityId: number,
      frame: StackFrame) {
    const line = frame.line,
      column = frame.column,
      length = frame.length;

    // highlight current node
    // TODO: still needs to be adapted to multi-activity system
    let ssId = getSectionId(sourceId,
                 {startLine: line, startColumn: column, charLength: length});
    let ss = document.getElementById(
                        getSectionIdForActivity(ssId, activityId));
    $(ss).addClass("DbgCurrentNode");

    this.showSourceById(sourceId, activityId);

    const sourcePaneId = getSectionIdForActivity(sourceId, activityId);

    // scroll to the statement
    $("html, body").animate({
      scrollTop: $("#" + sourcePaneId).offset().top
    }, 300);

    $("#" + sourcePaneId).animate({
      scrollTop: $(ss).offset().top
    }, 300);
  }


  showSourceById(sourceId: string, activityId: number) {
    if (this.getActiveSourceId(activityId) !== sourceId) {
      const actId = getActivityId(activityId);
      $("#" + actId + " .activity-sources-list li." + sourceId + " a").tab("show");
    }
  }

  getActiveSourceId(activityId: number): string {
    const actId = getActivityId(activityId);
    const actAndSourceId = $("#" + actId + " .tab-pane.active").attr("id");
    return getSourceIdFrom(actAndSourceId);
  }

  ensureBreakpointListEntry(breakpoint: Breakpoint) {
    if (breakpoint.checkbox !== null) {
      return;
    }

    let bpId = breakpoint.getListEntryId();
    let entry = nodeFromTemplate("breakpoint-tpl");
    entry.setAttribute("id", bpId);

    let tds = $(entry).find("td");
    tds[0].innerHTML = breakpoint.source.name;
    tds[1].innerHTML = breakpoint.getListEntryId();

    breakpoint.checkbox = $(entry).find("input");
    breakpoint.checkbox.attr("id", bpId + "chk");

    const list = document.getElementById("breakpoint-list");
    list.appendChild(entry);
  }

  updateBreakpoint(breakpoint: Breakpoint, highlightClass: string) {
    this.ensureBreakpointListEntry(breakpoint);
    const enabled = breakpoint.isEnabled();

    breakpoint.checkbox.prop("checked", enabled);
    const highlightElems = $(document.getElementsByClassName(
      breakpoint.getSourceElementClass()));
    if (enabled) {
      highlightElems.addClass(highlightClass);
    } else {
      highlightElems.removeClass(highlightClass);
    }
  }

  updateLineBreakpoint(bp: LineBreakpoint) {
    this.updateBreakpoint(bp, "breakpoint-active");
  }

  updateSendBreakpoint(bp: MessageBreakpoint) {
    this.updateBreakpoint(bp, "send-breakpoint-active");
  }

  updateAsyncMethodRcvBreakpoint(bp: MessageBreakpoint) {
    this.updateBreakpoint(bp, "send-breakpoint-active");
  }

  updatePromiseBreakpoint(bp: MessageBreakpoint) {
    this.updateBreakpoint(bp, "promise-breakpoint-active");
  }

  findActivityDebuggerButtons(activityId: number) {
    const id = getActivityId(activityId);
    const act = $("#" + id);
    return {
      resume:   act.find(".act-resume"),
      pause:    act.find(".act-pause"),
      stepInto: act.find(".act-step-into"),
      stepOver: act.find(".act-step-over"),
      return:   act.find(".act-return")
    };
  }

  switchActivityDebuggerToSuspendedState(activityId: number) {
    const btns = this.findActivityDebuggerButtons(activityId);

    btns.resume.removeClass("disabled");
    btns.pause.addClass("disabled");

    btns.stepInto.removeClass("disabled");
    btns.stepOver.removeClass("disabled");
    btns.return.removeClass("disabled");
  }

  switchActivityDebuggerToResumedState(activityId: number) {
    const btns = this.findActivityDebuggerButtons(activityId);

    btns.resume.addClass("disabled");
    btns.pause.removeClass("disabled");

    btns.stepInto.addClass("disabled");
    btns.stepOver.addClass("disabled");
    btns.return.addClass("disabled");
  }

  onContinueExecution(activityId: number) {
    this.switchActivityDebuggerToResumedState(activityId);

    const id = getActivityId(activityId);
    const highlightedNode = $("#" + id + " .DbgCurrentNode");
    highlightedNode.removeClass("DbgCurrentNode");
  }
}
