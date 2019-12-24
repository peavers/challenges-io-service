/* Licensed under Apache-2.0 */
package space.forloop.project.service;

import java.io.*;
import java.nio.file.Files;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Service;
import org.zeroturnaround.zip.ZipUtil;
import reactor.core.publisher.Mono;
import space.forloop.project.domain.Project;

/** @author Chris Turner (chris@forloop.space) */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitServiceImpl implements GitService {

  public static final String BUCKET_NAME = "challenges-f9f6b.appspot.com";

  private final FileService fileService;

  private final CloudStorageService cloudStorageService;

  @Override
  public Mono<Project> clone(final Project project) {

    try {
      final File workDirectory = Files.createTempDirectory(Instant.now().toString()).toFile();
      workDirectory.deleteOnExit();

      final Git git =
          Git.cloneRepository().setURI(project.getSource()).setDirectory(workDirectory).call();

      git.getRepository().close();

      final String uploadProject = uploadProject(workDirectory);
      log.info("uploaded to GCS {}", uploadProject);

      project.setDownloadUrl(uploadProject);
      project.setWorkingDirectory(workDirectory.getAbsolutePath());

      return fileService.importFiles(project);

    } catch (final GitAPIException | IOException e) {
      log.error("issue cloning repository {}", e.getMessage());
      return Mono.error(e);
    }
  }

  private String uploadProject(final File workDirectory) throws IOException {
    FileUtils.deleteDirectory(new File(String.format("%s/.git", workDirectory.getAbsolutePath())));
    log.info("removed .git directory");

    final File zipFile = zipDirectory(workDirectory);

    final InputStream targetStream = new DataInputStream(new FileInputStream(zipFile));

    return cloudStorageService.uploadFile(targetStream, zipFile.getName(), BUCKET_NAME);
  }

  private File zipDirectory(final File workDirectory) throws IOException {
    final File zipFile =
        Files.createTempFile("challenge-" + Instant.now().toEpochMilli(), ".zip").toFile();
    zipFile.deleteOnExit();

    ZipUtil.pack(workDirectory, zipFile);

    log.info("zipped directory {}", zipFile.getAbsolutePath());

    return zipFile;
  }
}
