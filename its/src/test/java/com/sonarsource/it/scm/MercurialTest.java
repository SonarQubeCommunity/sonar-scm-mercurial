/*
 * Mercurial :: Integration Tests
 * Copyright (C) 2014 ${owner}
 * sonarqube@googlegroups.com
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
package com.sonarsource.it.scm;

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.build.MavenBuild;
import com.sonar.orchestrator.locator.FileLocation;
import com.sonar.orchestrator.util.ZipUtils;
import com.sonar.orchestrator.version.Version;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.apache.commons.lang3.time.DateUtils;
import org.assertj.core.data.MapEntry;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.wsclient.jsonsimple.JSONArray;
import org.sonar.wsclient.jsonsimple.JSONObject;
import org.sonar.wsclient.jsonsimple.JSONValue;

import static org.assertj.core.api.Assertions.assertThat;

public class MercurialTest {

  private static final File REPO_DIR = new File("scm-repo");

  private static Version artifactVersion;

  private static Version artifactVersion() {
    if (artifactVersion == null) {
      try (FileInputStream fis = new FileInputStream(new File("../target/maven-archiver/pom.properties"))) {
        Properties props = new Properties();
        props.load(fis);
        artifactVersion = Version.create(props.getProperty("version"));
        return artifactVersion;
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
    return artifactVersion;
  }

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();

  @ClassRule
  public static Orchestrator orchestrator = Orchestrator.builderEnv()
    .addPlugin(FileLocation.of("../target/sonar-scm-mercurial-plugin-" + artifactVersion() + ".jar"))
    .setOrchestratorProperty("javaVersion", "LATEST_RELEASE")
    .addPlugin("java")
    .build();

  private boolean IS_5_2_OR_MORE = orchestrator.getServer().version().isGreaterThanOrEquals("5.2");

  private static File repoDir;

  @BeforeClass
  public static void init() throws Exception {
    repoDir = temp.newFolder();
  }

  @Before
  public void cleanup() throws Exception {
    orchestrator.resetData();
  }

  @Test
  public void testBlame() throws Exception {
    ZipUtils.unzip(new File(REPO_DIR, "dummy-hg.zip"), repoDir);

    File pom = new File(new File(repoDir, "dummy-hg"), "pom.xml");

    MavenBuild sonar = MavenBuild.create(pom)
      .setGoals("clean verify sonar:sonar")
      .setProperty("sonar.junit.reportsPath", "");
    orchestrator.executeBuilds(sonar);

    assertThat(getScmData("dummy-hg:dummy:src/main/java/org/dummy/Dummy.java"))
      .contains(
        MapEntry.entry(1, new LineData("f553ba9f524c", "2012-07-18T18:26:11+0200", "david@gageot.net")),
        MapEntry.entry(2, new LineData("f553ba9f524c", "2012-07-18T18:26:11+0200", "david@gageot.net")),
        MapEntry.entry(3, new LineData("f553ba9f524c", "2012-07-18T18:26:11+0200", "david@gageot.net")));
  }

  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
  private static final SimpleDateFormat DATETIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

  private class LineData {

    final String revision;
    final Date date;
    final String author;

    public LineData(String revision, String datetime, String author) throws ParseException {
      this.revision = IS_5_2_OR_MORE ? revision : null;
      this.date = IS_5_2_OR_MORE ? DATETIME_FORMAT.parse(datetime) : DateUtils.truncate(DATETIME_FORMAT.parse(datetime), Calendar.DAY_OF_MONTH);
      this.author = author;
    }

    public LineData(String date, String author) throws ParseException {
      this.revision = null;
      this.date = DATE_FORMAT.parse(date);
      this.author = author;
    }

    @Override
    public boolean equals(Object obj) {
      return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder().append(revision).append(date).append(author).toHashCode();
    }

    @Override
    public String toString() {
      return ToStringBuilder.reflectionToString(this, ToStringStyle.SIMPLE_STYLE);
    }
  }

  private Map<Integer, LineData> getScmData(String fileKey) throws ParseException {

    Map<Integer, LineData> result = new HashMap<Integer, LineData>();
    String json = orchestrator.getServer().adminWsClient().get("api/sources/scm", "commits_by_line", "true", "key", fileKey);
    JSONObject obj = (JSONObject) JSONValue.parse(json);
    JSONArray array = (JSONArray) obj.get("scm");
    for (int i = 0; i < array.size(); i++) {
      JSONArray item = (JSONArray) array.get(i);
      // Time part was added in 5.2
      String dateOrDatetime = (String) item.get(2);
      // Revision was added in 5.2
      result.put(((Long) item.get(0)).intValue(), IS_5_2_OR_MORE ? new LineData((String) item.get(3),
        dateOrDatetime, (String) item.get(1)) : new LineData(dateOrDatetime, (String) item.get(1)));
    }
    return result;
  }

}
