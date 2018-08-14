/*
 * SonarQube :: Plugins :: SCM :: Mercurial
 * Copyright (C) 2014-2018 SonarSource SA
 * mailto:info AT sonarsource DOT com
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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.mockito.stubbing.Answer;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultFileSystem;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.scm.BlameCommand.BlameInput;
import org.sonar.api.batch.scm.BlameCommand.BlameOutput;
import org.sonar.api.batch.scm.BlameLine;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.utils.DateUtils;
import org.sonar.api.utils.command.CommandExecutor;
import org.sonar.api.utils.command.StreamConsumer;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MercurialBlameCommandTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private DefaultFileSystem fs;
  private File baseDir;
  private BlameInput input;

  @Before
  public void prepare() throws IOException {
    baseDir = temp.newFolder();
    fs = new DefaultFileSystem(baseDir);
    input = mock(BlameInput.class);
    when(input.fileSystem()).thenReturn(fs);
  }

  @Test
  public void testParsingOfOutput() {
    InputFile inputFile = new TestInputFileBuilder("foo", "src/foo.xoo")
      .setLines(3)
      .setModuleBaseDir(baseDir.toPath())
      .build();
    fs.add(inputFile);

    BlameOutput result = mock(BlameOutput.class);
    CommandExecutor commandExecutor = mock(CommandExecutor.class);

    when(commandExecutor.execute(any(), any(), any(), anyLong())).thenAnswer((Answer<Integer>) invocation -> {
      StreamConsumer outConsumer = (StreamConsumer) invocation.getArguments()[1];
      outConsumer.consumeLine("Julien Henry <julien.henry@sonarsource.com> d45dafac0d9a Tue Nov 04 11:01:10 2014 +0100: foo");
      outConsumer.consumeLine("Julien Henry <julien.henry@sonarsource.com> d45dafac0d9a Tue Nov 04 11:01:10 2014 +0100: ");
      outConsumer.consumeLine("Julien Henry <julien.henry@sonarsource.com> d45dafac0d9a Tue Nov 04 11:01:10 2014 +0100: bar:baz");
      outConsumer.consumeLine("Jasper de Vries <jasper.de.vries@sonarsource.com> 2bc1af24477e Tue Sep 10 10:07:49 2013 +0200: foo");
      outConsumer.consumeLine("jasper.de.vries 2bc1af24477e Tue Sep 10 10:07:50 2013 +0200: bar:baz");
      outConsumer.consumeLine("julien.henry d45dafac0d9b Tue Nov 04 11:01:10 2014 +0100: baz");
      return 0;
    });

    when(input.filesToBlame()).thenReturn(singletonList(inputFile));
    new MercurialBlameCommand(commandExecutor, new MapSettings()).blame(input, result);
    verify(result).blameResult(inputFile,
      Arrays.asList(new BlameLine().date(DateUtils.parseDateTime("2014-11-04T11:01:10+0100")).revision("d45dafac0d9a").author("julien.henry@sonarsource.com"),
        new BlameLine().date(DateUtils.parseDateTime("2014-11-04T11:01:10+0100")).revision("d45dafac0d9a").author("julien.henry@sonarsource.com"),
        new BlameLine().date(DateUtils.parseDateTime("2014-11-04T11:01:10+0100")).revision("d45dafac0d9a").author("julien.henry@sonarsource.com"),
        new BlameLine().date(DateUtils.parseDateTime("2013-09-10T10:07:49+0200")).revision("2bc1af24477e").author("jasper.de.vries@sonarsource.com"),
        new BlameLine().date(DateUtils.parseDateTime("2013-09-10T10:07:50+0200")).revision("2bc1af24477e").author("jasper.de.vries"),
        new BlameLine().date(DateUtils.parseDateTime("2014-11-04T11:01:10+0100")).revision("d45dafac0d9b").author("julien.henry")));
  }

  @Test
  public void testAddMissingLastLine() {
    InputFile inputFile = new TestInputFileBuilder("foo", "src/foo.xoo")
      .setLines(4)
      .setModuleBaseDir(baseDir.toPath())
      .build();
    fs.add(inputFile);

    BlameOutput result = mock(BlameOutput.class);
    CommandExecutor commandExecutor = mock(CommandExecutor.class);

    when(commandExecutor.execute(any(), any(), any(), anyLong())).thenAnswer((Answer<Integer>) invocation -> {
      StreamConsumer outConsumer = (StreamConsumer) invocation.getArguments()[1];
      outConsumer.consumeLine("Julien Henry <julien.henry@sonarsource.com> d45dafac0d9a Tue Nov 04 11:01:10 2014 +0100: foo");
      outConsumer.consumeLine("Julien Henry <julien.henry@sonarsource.com> d45dafac0d9a Tue Nov 04 11:01:10 2014 +0100: ");
      outConsumer.consumeLine("Julien Henry <julien.henry@sonarsource.com> d45dafac0d9a Tue Nov 04 11:01:10 2014 +0100: bar");
      // Hg doesn't blame last empty line
      return 0;
    });

    when(input.filesToBlame()).thenReturn(singletonList(inputFile));
    new MercurialBlameCommand(commandExecutor, new MapSettings()).blame(input, result);
    verify(result).blameResult(inputFile,
      Arrays.asList(
        new BlameLine().date(DateUtils.parseDateTime("2014-11-04T11:01:10+0100")).revision("d45dafac0d9a").author("julien.henry@sonarsource.com"),
        new BlameLine().date(DateUtils.parseDateTime("2014-11-04T11:01:10+0100")).revision("d45dafac0d9a").author("julien.henry@sonarsource.com"),
        new BlameLine().date(DateUtils.parseDateTime("2014-11-04T11:01:10+0100")).revision("d45dafac0d9a").author("julien.henry@sonarsource.com"),
        new BlameLine().date(DateUtils.parseDateTime("2014-11-04T11:01:10+0100")).revision("d45dafac0d9a").author("julien.henry@sonarsource.com")));
  }

  @Test
  public void empty_file_has_always_an_empty_last_line() {
    InputFile inputFile = new TestInputFileBuilder("foo", "src/foo.xoo")
      .setLines(1)
      .setModuleBaseDir(baseDir.toPath())
      .build();
    fs.add(inputFile);

    BlameOutput result = mock(BlameOutput.class);
    CommandExecutor commandExecutor = mock(CommandExecutor.class);

    when(commandExecutor.execute(any(), any(), any(), anyLong())).thenAnswer((Answer<Integer>) invocation -> {
      // Hg doesn't blame last empty line
      return 0;
    });

    when(input.filesToBlame()).thenReturn(singletonList(inputFile));
    new MercurialBlameCommand(commandExecutor, new MapSettings()).blame(input, result);
    verify(result).blameResult(inputFile, emptyList());
  }

  @Test
  public void shouldNotFailOnFileUncommitted() {
    InputFile inputFile = new TestInputFileBuilder("foo", "src/foo.xoo")
      .setModuleBaseDir(baseDir.toPath())
      .build();
    fs.add(inputFile);

    BlameOutput result = mock(BlameOutput.class);
    CommandExecutor commandExecutor = mock(CommandExecutor.class);

    when(commandExecutor.execute(any(), any(), any(), anyLong())).thenAnswer((Answer<Integer>) invocation -> {
      StreamConsumer errConsumer = (StreamConsumer) invocation.getArguments()[2];
      errConsumer.consumeLine("abandon : src/foo.xoo: no such file in rev 000000000000");
      return 255;
    });

    when(input.filesToBlame()).thenReturn(singletonList(inputFile));
    new MercurialBlameCommand(commandExecutor, new MapSettings()).blame(input, result);

    // TODO assert log contains
    // "The mercurial blame command [hg blame -w -v --user --date --changeset src/foo.xoo] failed: abandon : src/foo.xoo: no such file in
    // rev 000000000000"
  }

}
