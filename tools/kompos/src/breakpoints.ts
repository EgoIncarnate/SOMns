import {Source, SourceCoordinate, AbstractBreakpointData, LineBreakpointData,
  SectionBreakpointData, SectionBreakpointType,
  createLineBreakpointData, createSectionBreakpointData} from './messages';

export type Breakpoint = LineBreakpoint | MessageBreakpoint;

abstract class AbstractBreakpoint<T extends AbstractBreakpointData> {
  readonly data: T;
  checkbox: any;
  readonly source: Source;

  constructor(data: T, source: Source) {
    this.data     = data;
    this.checkbox = null;
    this.source   = source;
  }

  /**
   * @return a unique id for the breakpoint, to be used in the view as HTML id
   */
  getId() {
    return 'bp:';
  }

  toggle() {
    this.data.enabled = !this.data.enabled;
  }

  isEnabled() {
    return this.data.enabled;
  }
}

export class LineBreakpoint extends AbstractBreakpoint<LineBreakpointData> {
  readonly lineNumSpan: Element;
  readonly sourceId: string;

  constructor(data: LineBreakpointData, source: Source, sourceId: string,
      lineNumSpan: Element) {
    super(data, source);
    this.lineNumSpan = lineNumSpan;
    this.sourceId    = sourceId;
  }

  getId(): string {
    return super.getId() + this.sourceId + ':' + this.data.line;
  }
}

export class MessageBreakpoint extends AbstractBreakpoint<SectionBreakpointData> {
  readonly sectionId: string;

  constructor(data: SectionBreakpointData, source: Source, sectionId: string) {
    super(data, source);
    this.sectionId = sectionId;
  }

  getId(): string {
    return super.getId() + this.data.type;
  }
}

export function createLineBreakpoint(source: Source, sourceId: string,
    line: number, clickedSpan: Element) {
  return new LineBreakpoint(createLineBreakpointData(source.uri, line, false),
    source, sourceId, clickedSpan);
}

export function createMsgBreakpoint(source: Source,
    sourceSection: SourceCoordinate, sectionId: string,
    type: SectionBreakpointType) {
  return new MessageBreakpoint(
    createSectionBreakpointData(source.uri, sourceSection.startLine,
      sourceSection.startColumn, sourceSection.charLength, type, false),
    source, sectionId);
}
