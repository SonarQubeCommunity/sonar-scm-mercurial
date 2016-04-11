/*
 * SonarQube :: Plugins :: SCM :: Mercurial
 * Copyright (C) 2014-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.plugins.scm.mercurial;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.scm.BlameLine;
import org.sonar.api.utils.command.StreamConsumer;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MercurialBlameConsumer implements StreamConsumer {

  private static final Logger LOG = LoggerFactory.getLogger(MercurialBlameConsumer.class);

  private static final String HG_TIMESTAMP_PATTERN = "EEE MMM dd HH:mm:ss yyyy Z";
  private static final String HG_BLAME_PATTERN = "(.*?) (?:<(.*)> )?([0-9a-f]{12}) ([^:]+:[^:]+:[^:]+):.*";

  private List<BlameLine> lines = new ArrayList<BlameLine>();

  private DateFormat format;

  private final String filename;

  private Pattern pattern;

  public MercurialBlameConsumer(String filename) {
    this.filename = filename;
    format = new SimpleDateFormat(HG_TIMESTAMP_PATTERN, Locale.ENGLISH);
    pattern = Pattern.compile(HG_BLAME_PATTERN);
  }

  @Override
  public void consumeLine(String line) {
    String trimmedLine = line.trim();
    /* godin <email> 0 Sun Jan 31 03:04:54 2010 +0300: */
    /* godin 0 Sun Jan 31 03:04:54 2010 +0300: */
    Matcher matcher = pattern.matcher(trimmedLine);
    if (!matcher.matches()) {
      throw new IllegalStateException("Unable to blame file " + filename + ". Unrecognized blame info at line " + (getLines().size() + 1) + ": " + trimmedLine);
    }
    String author = matcher.group(2);
    if (author == null) {
      author = matcher.group(1);
    }
    String revision = matcher.group(3);
    String dateStr = matcher.group(4);
    Date dateTime = null;
    try {
      dateTime = format.parse(dateStr);
    } catch (ParseException e) {
      LOG.warn(
        "skip ParseException on file " + filename + " at line " + (getLines().size() + 1) + ": " + e.getMessage() + " during parsing date " + dateStr
          + " with pattern " + HG_TIMESTAMP_PATTERN + " [" + line + "]", e);
    }

    lines.add(new BlameLine().date(dateTime).revision(revision).author(author));
  }

  public List<BlameLine> getLines() {
    return lines;
  }
}
