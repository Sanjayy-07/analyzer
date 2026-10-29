package com.archanalyzer.refactor;

public class RefactoringChange {
    public final String filePath;
    public final String description;

    public RefactoringChange(String filePath, String description) {
        this.filePath = filePath;
        this.description = description;
    }

    @Override
    public String toString() { return filePath + " :: " + description; }
}
