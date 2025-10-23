package com.aidebugger.backend.service;

import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.aidebugger.backend.model.CodeRequest;
import com.aidebugger.backend.model.CodeResponse;

@Service
public class LocalExecutorService {

	private static final long TIMEOUT_SECONDS = 5;
	private static final int MAX_OUTPUT_CHARS = 20000;

	public CodeResponse executeCode(CodeRequest request) {
		CodeResponse resp = new CodeResponse();
		String lang = request.getLanguage() == null ? "" : request.getLanguage().toLowerCase();
		try {
			Path tempDir = Files.createTempDirectory("codeexec_");
			try {
				if (lang.equals("c")) {
					return runC(request, tempDir);
				} else if (lang.equals("java")) {
					return runJava(request, tempDir);
				} else if (lang.equals("python") || lang.equals("py")) {
					return runPython(request, tempDir);
				} else {
					resp.setError("Unsupported language: " + request.getLanguage());
					return resp;
				}
			} finally {
				try { deleteRecursive(tempDir); } catch (Exception ignored) {}
			}
		} catch (IOException e) {
			resp.setError("Server filesystem error: " + e.getMessage());
			return resp;
		}
	}

	private CodeResponse runC(CodeRequest request, Path dir) {
		CodeResponse resp = new CodeResponse();
		try {
			Path src = dir.resolve("main.c");
			Files.writeString(src, request.getCode(), StandardCharsets.UTF_8);

			ProcessBuilder pbCompile = new ProcessBuilder("gcc", src.toString(), "-o", dir.resolve("main").toString());
			pbCompile.directory(dir.toFile());
			Process compileProc = pbCompile.start();

			String compileErr = readStream(compileProc.getErrorStream());
			compileProc.waitFor(10, TimeUnit.SECONDS);

			if (compileErr != null && !compileErr.isBlank()) {
				resp.setCompileOutput(truncate(compileErr));
				return resp;
			}

			ProcessBuilder pbRun = new ProcessBuilder(dir.resolve("main").toString());
			pbRun.directory(dir.toFile());
			Process runProc = pbRun.start();
			if (request.getInput() != null) {
				try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(runProc.getOutputStream()))) {
					w.write(request.getInput());
					w.flush();
				}
			} else {
				runProc.getOutputStream().close();
			}

			boolean finished = runProc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
			if (!finished) {
				runProc.destroyForcibly();
				resp.setError("Execution timed out after " + TIMEOUT_SECONDS + "s");
				return resp;
			}

			String stdout = readStream(runProc.getInputStream());
			String stderr = readStream(runProc.getErrorStream());

			if (stderr != null && !stderr.isBlank()) resp.setError(truncate(stderr));
			if (stdout != null) resp.setOutput(truncate(stdout));
			return resp;
		} catch (Exception e) {
			resp.setError("Execution error: " + e.getMessage());
			return resp;
		}
	}

	private CodeResponse runJava(CodeRequest request, Path dir) {
		CodeResponse resp = new CodeResponse();
		try {
			String code = request.getCode();
			String className = extractPublicClassName(code);
			if (className == null) {
				className = "Main";
			}
			Path src = dir.resolve(className + ".java");
			Files.writeString(src, code, StandardCharsets.UTF_8);

			ProcessBuilder pbCompile = new ProcessBuilder("javac", src.toString());
			pbCompile.directory(dir.toFile());
			Process compileProc = pbCompile.start();

			String compileErr = readStream(compileProc.getErrorStream());
			compileProc.waitFor(10, TimeUnit.SECONDS);

			if (compileErr != null && !compileErr.isBlank()) {
				resp.setCompileOutput(truncate(compileErr));
				return resp;
			}

			ProcessBuilder pbRun = new ProcessBuilder("java", "-cp", dir.toString(), className);
			pbRun.directory(dir.toFile());
			Process runProc = pbRun.start();

			if (request.getInput() != null) {
				try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(runProc.getOutputStream()))) {
					w.write(request.getInput());
					w.flush();
				}
			} else {
				runProc.getOutputStream().close();
			}

			boolean finished = runProc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
			if (!finished) {
				runProc.destroyForcibly();
				resp.setError("Execution timed out after " + TIMEOUT_SECONDS + "s");
				return resp;
			}

			String stdout = readStream(runProc.getInputStream());
			String stderr = readStream(runProc.getErrorStream());

			if (stderr != null && !stderr.isBlank()) resp.setError(truncate(stderr));
			if (stdout != null) resp.setOutput(truncate(stdout));
			return resp;
		} catch (Exception e) {
			resp.setError("Execution error: " + e.getMessage());
			return resp;
		}
	}

	private CodeResponse runPython(CodeRequest request, Path dir) {
		CodeResponse resp = new CodeResponse();
		try {
			Path src = dir.resolve("main.py");
			Files.writeString(src, request.getCode(), StandardCharsets.UTF_8);

			String pythonCmd = detectPythonCommand();

			ProcessBuilder pbRun = new ProcessBuilder(pythonCmd, src.toString());
			pbRun.directory(dir.toFile());
			Process runProc = pbRun.start();

			if (request.getInput() != null) {
				try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(runProc.getOutputStream()))) {
					w.write(request.getInput());
					w.flush();
				}
			} else {
				runProc.getOutputStream().close();
			}

			boolean finished = runProc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
			if (!finished) {
				runProc.destroyForcibly();
				resp.setError("Execution timed out after " + TIMEOUT_SECONDS + "s");
				return resp;
			}

			String stdout = readStream(runProc.getInputStream());
			String stderr = readStream(runProc.getErrorStream());

			if (stderr != null && !stderr.isBlank()) resp.setError(truncate(stderr));
			if (stdout != null) resp.setOutput(truncate(stdout));
			return resp;
		} catch (Exception e) {
			resp.setError("Execution error: " + e.getMessage());
			return resp;
		}
	}

	private String detectPythonCommand() {
		String[] candidates = new String[] { "python3", "python", "py" };
		for (String cmd : candidates) {
			try {
				ProcessBuilder pb = new ProcessBuilder(cmd, "--version");
				Process p = pb.start();
				boolean ok = p.waitFor(2, TimeUnit.SECONDS);
				if (ok && p.exitValue() == 0) {
					// found a working python command
					return cmd;
				}
			} catch (IOException | InterruptedException ignored) {
				// try next candidate
			}
		}
		// fallback: return "python" (will likely produce the same error if none found)
		return "python";
	}


	private String readStream(InputStream is) throws IOException {
		if (is == null) return "";
		StringBuilder sb = new StringBuilder();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
			int c;
			while ((c = br.read()) != -1 && sb.length() < MAX_OUTPUT_CHARS) {
				sb.append((char) c);
			}
		}
		return sb.toString();
	}

	private String truncate(String s) {
		if (s == null) return null;
		if (s.length() <= MAX_OUTPUT_CHARS) return s;
		return s.substring(0, MAX_OUTPUT_CHARS) + "\n...[truncated]";
	}

	private void deleteRecursive(Path path) throws IOException {
		if (Files.notExists(path)) return;
		Files.walk(path)
		.sorted(Comparator.reverseOrder())
		.map(Path::toFile)
		.forEach(File::delete);
	}

	private String extractPublicClassName(String javaSource) {
		Pattern p = Pattern.compile("public\\s+class\\s+(\\w+)");
		Matcher m = p.matcher(javaSource);
		if (m.find()) {
			return m.group(1);
		}
		return null;
	}
}
