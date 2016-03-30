/**
 * Copyright (c) 2016 Stefan Marr
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tools.highlight;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.JSONHelper;
import com.oracle.truffle.api.utilities.JSONHelper.JSONArrayBuilder;
import com.oracle.truffle.api.utilities.JSONHelper.JSONObjectBuilder;


public final class JsonWriter {

  public static void fileOut(final String outputFile,
      final Map<SourceSection, Set<Class<? extends Tags>>> sourceSectionTags) {
    new JsonWriter(outputFile, sourceSectionTags).createJsonFile();
  }

  private final String outputFile;
  private final Map<SourceSection, Set<Class<? extends Tags>>> sourceSectionTags;

  private JsonWriter(final String outputFile,
      final Map<SourceSection, Set<Class<? extends Tags>>> sourceSectionTags) {
    this.sourceSectionTags = sourceSectionTags;
    this.outputFile = outputFile;
  }

  public void createJsonFile() {
    Set<SourceSection> allSections = sourceSectionTags.keySet();

    Set<Source> allSources = new HashSet<>();
    allSections.forEach(ss -> allSources.add(ss.getSource()));

    // TODO:
//    for (Source s : allSources) {
//      Set<SourceSection> annotations = SourcecodeCompiler.getSyntaxAnnotations(s);
//      if (annotations != null) {
//        allSections.addAll(annotations);
//      }
//    }

    Map<Source, String>        sourceToId  = createIdMap(allSources, "s-");
    Map<SourceSection, String> sectionToId = createIdMap(allSections, "ss-");

    JSONObjectBuilder allSourcesJson = JSONHelper.object();
    for (Source s : allSources) {
      String id = sourceToId.get(s);
      assert id != null && !id.equals("");
      allSourcesJson.add(id, sourceToJson(s, id));
    }

    JSONObjectBuilder allSectionsJson = JSONHelper.object();
    for (SourceSection ss : allSections) {
      allSectionsJson.add(sectionToId.get(ss), sectionToJson(ss, sectionToId.get(ss), sourceToId));
    }

    JSONObjectBuilder root = JSONHelper.object();
    root.add("sources", allSourcesJson);
    root.add("sections", allSectionsJson);

    try {
      try (PrintWriter jsonFile = new PrintWriter(new File(outputFile))) {
        jsonFile.println(root.toString());
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private <U> Map<U, String> createIdMap(final Set<U> set, final String idPrefix) {
    Map<U, String> eToId = new HashMap<>();

    int i = 0;
    for (U e : set) {
      eToId.put(e, idPrefix + i);
      i += 1;
    }
    return eToId;
  }

  private JSONObjectBuilder sourceToJson(final Source s, final String id) {
    JSONObjectBuilder builder = JSONHelper.object();
    builder.add("id", id);
    builder.add("sourceText", s.getCode());
    builder.add("mimeType", s.getMimeType());
    builder.add("name", s.getName());
    builder.add("shortName", s.getShortName());
    return builder;
  }

  private JSONObjectBuilder sectionToJson(final SourceSection ss,
      final String id, final Map<Source, String> sourceToId) {
    JSONObjectBuilder builder = JSONHelper.object();

    builder.add("id", id);
    builder.add("firstIndex", ss.getCharIndex());
    builder.add("length", ss.getCharLength());
    builder.add("identifier", ss.getIdentifier());
    builder.add("description", ss.getShortDescription());
    builder.add("sourceId", sourceToId.get(ss.getSource()));

    Set<Class<? extends Tags>> tags = sourceSectionTags.get(ss);
    if (tags.size() > 0) {
      JSONArrayBuilder arr = JSONHelper.array();
      for (Class<? extends Tags> tagClass : tags) {
        arr.add(tagClass.getSimpleName());
      }
      builder.add("tags", arr);
    }
//    builder.add("data", collectDataForSection(ss));

    return builder;
  }
}
