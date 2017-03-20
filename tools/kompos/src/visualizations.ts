/* jshint -W097 */
"use strict";

import {SymbolMessage, Activity, ActivityType} from "./messages";
import * as d3 from "d3";
import {HistoryData, ActivityNode, ActivityLink} from "./history-data";

// Tango Color Scheme: http://emilis.info/other/extended_tango/
const TANGO_SCHEME = [
  ["#2e3436", "#555753", "#888a85", "#babdb6", "#d3d7cf", "#ecf0eb", "#f7f8f5"],
  ["#291e00", "#725000", "#c4a000", "#edd400", "#fce94f", "#fffc9c", "#feffd0"],
  ["#301700", "#8c3700", "#ce5c00", "#f57900", "#fcaf3e", "#ffd797", "#fff0d7"],
  ["#271700", "#503000", "#8f5902", "#c17d11", "#e9b96e", "#efd0a7", "#faf0d7"],
  ["#173000", "#2a5703", "#4e9a06", "#73d216", "#8ae234", "#b7f774", "#e4ffc7"],
  ["#00202a", "#0a3050", "#204a87", "#3465a4", "#729fcf", "#97c4f0", "#daeeff"],
  ["#170720", "#371740", "#5c3566", "#75507b", "#ad7fa8", "#e0c0e4", "#fce0ff"],
  ["#270000", "#600000", "#a40000", "#cc0000", "#ef2929", "#f78787", "#ffcccc"]];

function getTangoLightToDarker() {
  const result = [];
  for (const column of [5, 4, 3]) {
    for (const row of TANGO_SCHEME) {
      result.push(row[column]);
    }
  }
  return result;
}

const TANGO_COLORS = getTangoLightToDarker();

export class SystemVisualization {
  private data: HistoryData;

  constructor() {
    this.data = new HistoryData();
  }

  public reset() {
    this.data = new HistoryData();
  }

  public updateStringData(msg: SymbolMessage) {
    this.data.addStrings(msg.ids, msg.symbols);
  }

  public updateData(dv: DataView): Activity[] {
    return this.data.updateDataBin(dv);
  }

  public display() {
    const canvas = $("#graph-canvas");
    // colors = d3.scale.category10();
    // colors = d3.scale.ordinal().domain().range(tango)
    canvas.empty();

    let zoom = d3.behavior.zoom()
      .scaleExtent([0.1, 10])
      .on("zoom", zoomed);

    const svg = d3.select("#graph-canvas")
      .append("svg")
      // .attr("oncontextmenu", "return false;")
      .attr("width", canvas.width())
      .attr("height", canvas.height())
      .attr("style", "background: none;")
      .call(zoom);

    // set up initial nodes and links
    //  - nodes are known by "id", not by index in array.
    //  - reflexive edges are indicated on the node (as a bold black circle).
    //  - links are always source < target; edge directions are set by "left" and "right".

    nodes = this.data.getActivityNodes();
    links = this.data.getLinks();

    // init D3 force layout
    force = d3.layout.force()
      .nodes(nodes)
      .links(links)
      .size([canvas.width(), canvas.height()])
      .linkDistance(70)
      .charge(-500)
      .on("tick", tick);

    force.linkStrength(function(link) {
      return link.messageCount / this.data.getMaxMessageSends();
    });

    // define arrow markers for graph links
    createArrowMarker(svg, "end-arrow",   6, "M0,-5L10,0L0,5",  "#000");
    createArrowMarker(svg, "start-arrow", 4, "M10,-5L0,0L10,5", "#000");

    createArrowMarker(svg, "end-arrow-creator",   6, "M0,-5L10,0L0,5",  "#aaa");
    createArrowMarker(svg, "start-arrow-creator", 4, "M10,-5L0,0L10,5", "#aaa");

    // handles to link and node element groups
    path = svg.append("svg:g").selectAll("path");
    circle = svg.append("svg:g").selectAll("g");

    restart();
  }

}

let path, circle, nodes: ActivityNode[], links: ActivityLink[], force;

function createArrowMarker(svg: d3.Selection<any>, id: string, refX: number,
    d: string, color: string) {
  svg.append("svg:defs").append("svg:marker")
    .attr("id", id)
    .attr("viewBox", "0 -5 10 10")
    .attr("refX", refX)
    .attr("markerWidth", 3)
    .attr("markerHeight", 3)
    .attr("orient", "auto")
    .append("svg:path")
    .attr("d", d)
    .attr("fill", color);
}

let zoomScale = 1;
let zoomTransl = [0, 0];

function zoomed() {
  let zoomEvt: d3.ZoomEvent = <d3.ZoomEvent> d3.event;
  zoomScale  = zoomEvt.scale;
  zoomTransl = zoomEvt.translate;

  circle.attr("transform", function (d) {
    const x = zoomTransl[0] + d.x * zoomScale;
    const y = zoomTransl[1] + d.y * zoomScale;
    return "translate(" + x + "," + y + ")scale(" + zoomScale + ")"; });
  path.attr("transform", "translate(" + zoomTransl + ")scale(" + zoomScale + ")");
}

// update force layout (called automatically each iteration)
function tick() {
  // draw directed edges with proper padding from node centers
  path.attr("d", function(d: ActivityLink) {
    const deltaX = d.target.x - d.source.x,
      deltaY = d.target.y - d.source.y,
      dist = Math.sqrt(deltaX * deltaX + deltaY * deltaY),
      normX = deltaX / dist,
      normY = deltaY / dist,
      sourcePadding = d.left ? 17 : 12,
      targetPadding = d.right ? 17 : 12,
      sourceX = d.source.x + (sourcePadding * normX),
      sourceY = d.source.y + (sourcePadding * normY),
      targetX = d.target.x - (targetPadding * normX),
      targetY = d.target.y - (targetPadding * normY);
      console.assert(!Number.isNaN(sourceX));
    return "M" + sourceX + "," + sourceY + "L" + targetX + "," + targetY;
  });

  circle.attr("transform", function(d: ActivityNode) {
    return "translate(" + (zoomTransl[0] + d.x * zoomScale) + "," + (zoomTransl[1] + d.y * zoomScale) + ")scale(" + zoomScale + ")";
  });
}

function selectStartMarker(d: ActivityLink) {
  return d.left
    ? (d.creation ? "url(#start-arrow-creator)" : "url(#start-arrow)")
    : "";
}

function selectEndMarker(d: ActivityLink) {
  return d.right
    ? (d.creation ? "url(#end-arrow-creator)" : "url(#end-arrow)")
    : "";
}

// update graph (called when needed)
function restart() {
  // path (link) group
  path = path.data(links);

  // update existing links
  path // .classed("selected", function(d) { return d === selected_link; })
    .style("marker-start", selectStartMarker)
    .style("marker-end",   selectEndMarker);

  // add new links
  path.enter().append("svg:path")
    .attr("class", function (d: ActivityLink) {
      return d.creation
        ? "creation-link"
        : "link";
    })
    // .classed("selected", function(d) { return d === selected_link; })
    .style("marker-start", selectStartMarker)
    .style("marker-end",   selectEndMarker);

  // remove old links
  path.exit().remove();

  // circle (node) group
  // NB: the function arg is crucial here! nodes are known by id, not by index!
  circle = circle.data(nodes, function(a: ActivityNode) { return a.getDataId(); });

  // add new nodes
  const g = circle.enter().append("svg:g");

  createActivity(g);

  // After rendering text, adapt rectangles
  adaptRectSizeAndTextPostion();

  // Enable dragging of nodes
  g.call(force.drag);

  // remove old nodes
  circle.exit().remove();

  // set the graph in motion
  force.start();

  // execute enough steps that the graph looks static
  for (let i = 0; i < 1000; i++) {
    force.tick();
  }
//   force.stop();
}

function createActivity(g) {
  g.attr("id", function (a: ActivityNode) { return a.getSystemViewId(); });

  createActivityRectangle(g);
  createActivityLabel(g);
  createActivityStatusIndicator(g);
}

function createActivityRectangle(g) {
  g.append("rect")
    .attr("rx", 6)
    .attr("ry", 6)
    .attr("x", -12.5)
    .attr("y", -12.5)
    .attr("width", 50)
    .attr("height", 25)
    .on("mouseover", function(a: ActivityNode) { return ctrl.overActivity(a, this); })
    .on("mouseout",  function(a: ActivityNode) { return ctrl.outActivity(a, this); })
    .attr("class", "node")
    .style("fill", function(_, i) {
      return TANGO_COLORS[i];
    })
    .style("stroke", function(_, i) { return d3.rgb(TANGO_COLORS[i]).darker().toString(); })
    .style("stroke-width", function(a: ActivityNode) { return (a.getGroupSize() > 1) ? Math.log(a.getGroupSize()) * 3 : ""; })
    .classed("reflexive", function(a: ActivityNode) { return a.reflexive; });
}

function createActivityLabel(g) {
  g.append("svg:text")
    .attr("x", 0)
    .attr("dy", ".35em")
    .attr("class", "id")
    .html(function(a: ActivityNode) {
      let label = getTypePrefix(a.getType()) + a.getName();

      if (a.getGroupSize() > 1) {
        label += " (" + a.getGroupSize() + ")";
      }
      return label;
    });
}

function createActivityStatusIndicator(g) {
  g.append("svg:text")
    .attr("x", 10)
    .attr("dy", "-.35em")
    .attr("class", function(a: ActivityNode) {
      return "activity-pause" + (a.isRunning() ? " running" : "");
    })
    .html("&#xf04c;");
}

const PADDING = 15;

function getTypePrefix(type: ActivityType) {
  switch (type) {
    case "Actor":
      return "&#128257; ";
    case "Process":
      return "&#10733;";
    case "Thread":
      return "&#11123;";
    case "Task":
      return "&#8623;";
    default:
      console.warn("getTypePrefix misses support for " + type);
      return null;
  }
}

function createChannelBody(g, x: number, y: number) {
  return g.append("rect")
    .attr("x", x + 5)
    .attr("y", y + 1)
    .attr("width", 20)
    .attr("height", 8);
}

function createChannelEnd(g, x: number, y: number) {
  return g.append("path")
    .attr("d", `M ${x} ${y} L ${x + 6} ${y} L ${x + 10} ${y + 5} L ${x + 6} ${y + 10} L ${x} ${y + 10} L ${x + 4} ${y + 5} Z`)
    .attr("stroke", "black")
    .attr("stroke-linecap", "round")
    .attr("stroke-linejoin", "round")
    .attr("stroke-width", 1)
    .attr("fill", "#f3f3f3");
}

function createChannel(g, x: number, y: number) {
  createChannelBody(g, x, y);
  createChannelEnd(g, x, y);
  createChannelEnd(g, x + 20, y);
}

function adaptRectSizeAndTextPostion() {
  d3.selectAll("rect")
    .attr("width", function() {
      return this.parentNode.childNodes[1].getComputedTextLength() + PADDING;
     })
    .attr("x", function() {
      const width = this.parentNode.childNodes[1].getComputedTextLength();
      d3.select(this.parentNode.childNodes[2]).attr("x", (width / 2.0) + 3.0);
      return - (PADDING + width) / 2.0;
    });
}
