(function () {
    const dropzone = document.getElementById('dropzone');
    const fileInput = document.getElementById('fileInput');
    const label = document.getElementById('fileNameLabel');

    const uploadForm = document.getElementById('uploadForm');
    const submitBtn = document.getElementById('submitBtn');
    const loadingIndicator = document.getElementById('loadingIndicator');
    const clientError = document.getElementById('clientError');

    dropzone.addEventListener('click', () => fileInput.click());

    ['dragenter', 'dragover'].forEach(evt =>
        dropzone.addEventListener(evt, e => {
            e.preventDefault();
            dropzone.classList.add('dragover');
        })
    );

    ['dragleave', 'drop'].forEach(evt =>
        dropzone.addEventListener(evt, e => {
            e.preventDefault();
            dropzone.classList.remove('dragover');
        })
    );

    dropzone.addEventListener('drop', e => {
        const files = e.dataTransfer.files;
        if (files.length > 0) {
            fileInput.files = files;
            label.textContent = files[0].name;
            if (clientError) clientError.style.display = 'none';
        }
    });

    fileInput.addEventListener('change', () => {
        if (fileInput.files.length > 0) {
            label.textContent = fileInput.files[0].name;
            if (clientError) clientError.style.display = 'none';
        }
    });

    if (uploadForm) {
        uploadForm.addEventListener('submit', (e) => {
            if (!fileInput.files || fileInput.files.length === 0) {
                e.preventDefault();
                if (clientError) {
                    clientError.textContent = 'Please choose a Java project archive (.zip, .tar, .tar.gz) before submitting.';
                    clientError.style.display = 'block';
                }
                return;
            }

            // Prevent duplicate submits and show loading feedback
            if (submitBtn) {
                submitBtn.disabled = true;
                submitBtn.textContent = 'Analyzing Java Project...';
                submitBtn.style.opacity = '0.6';
                submitBtn.style.cursor = 'not-allowed';
            }
            if (loadingIndicator) {
                loadingIndicator.style.display = 'block';
            }
        });
    }
})();
