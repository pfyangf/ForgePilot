package com.forgepilot.delivery.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 廉价检测：按路径规则识别测试文件变更，供审核页提示。 */
@Component
public class TestFileChangeDetector {

    private static final Pattern TEST_PATH = Pattern.compile(
            "(^|.*/)(src/test/|src/tests/|__tests__/|test/|tests/).*"
                    + "|.*Test\\.java$|.*Tests\\.java$|.*\\.spec\\.(ts|js|tsx|jsx)$|.*\\.test\\.(ts|js|tsx|jsx)$",
            Pattern.CASE_INSENSITIVE);

    public List<String> findTestFileChanges(List<String> nameStatusLines) {
        List<String> hits = new ArrayList<>();
        if (nameStatusLines == null) {
            return hits;
        }
        for (String line : nameStatusLines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String path = line;
            int tab = line.indexOf('\t');
            if (tab >= 0) {
                path = line.substring(tab + 1).trim();
            } else if (line.length() > 2 && line.charAt(1) == ' ') {
                path = line.substring(2).trim();
            }
            // rename: R100\told\tnew
            if (path.contains("\t")) {
                String[] parts = path.split("\t");
                path = parts[parts.length - 1];
            }
            if (TEST_PATH.matcher(path).matches()) {
                hits.add(line.trim());
            }
        }
        return hits;
    }

    public boolean looksLikeTestPath(String path) {
        return path != null && TEST_PATH.matcher(path).matches();
    }
}
