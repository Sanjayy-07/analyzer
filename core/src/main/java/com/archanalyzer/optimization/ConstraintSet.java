package com.archanalyzer.optimization;

/** PART 16: developer-specified constraints the optimizer must respect. */
public class ConstraintSet {
    public int maxFilesChanged = 15;
    public boolean noNewExternalDependencies = true;
    public boolean preservePackageStructure = false;
    public boolean preservePublicApi = true;
    public boolean preferLowRisk = true;
    public boolean preferMaintainability = true;
    public boolean preferMinimumDependencies = false;
    public boolean preferMinimumChanges = false;

    public static ConstraintSet defaults() { return new ConstraintSet(); }

    /** Returns true if a candidate's projected change footprint satisfies these constraints. */
    public boolean isSatisfiedBy(ArchitectureCandidate candidate) {
        return candidate.filesChanged <= maxFilesChanged
            && (!noNewExternalDependencies || !candidate.introducesExternalDependency)
            && (!preservePublicApi || candidate.preservesPublicApi);
    }
}
