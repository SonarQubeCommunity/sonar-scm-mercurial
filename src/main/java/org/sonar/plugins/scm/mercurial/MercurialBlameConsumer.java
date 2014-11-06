/*
 * SonarQube :: Plugins :: SCM :: Mercurial
 * Copyright (C) 2009 ${owner}
 * dev@sonar.codehaus.org
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
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
  private static final String HG_BLAME_PATTERN = ".* <(.*)> ([0-9a-f]+) (.*):.*";

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
    /* godin <email> 0 Sun Jan 31 03:04:54 2010 +0300 */
    Matcher matcher = pattern.matcher(trimmedLine);
    if (!matcher.matches()) {
      throw new IllegalStateException("Unable to blame file " + filename + ". Unrecognized blame info at line " + (getLines().size() + 1) + ": " + trimmedLine);
    }
    String authorEmail = matcher.group(1);
    String revision = matcher.group(2);
    String dateStr = matcher.group(3);
    Date dateTime = parseDate(dateStr);

    lines.add(new BlameLine().date(dateTime).revision(revision).author(authorEmail));
  }

  /**
   * Converts the date timestamp from the output into a date object.
   *
   * @return A date representing the timestamp of the log entry.
   */
  protected Date parseDate(String date) {
    try {
      return format.parse(date);
    } catch (ParseException e) {
      LOG.warn(
        "skip ParseException: " + e.getMessage() + " during parsing date " + date
          + " with pattern " + HG_TIMESTAMP_PATTERN + " with Locale " + Locale.ENGLISH, e);
      return null;
    }
  }

  public List<BlameLine> getLines() {
    return lines;
  }
}
