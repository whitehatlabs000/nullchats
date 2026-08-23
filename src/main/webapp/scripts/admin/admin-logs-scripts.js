

$(document).ready(function() {


    $.ajaxSetup({
        beforeSend: function(xhr) {

            const token = $('meta[name="csrf-token"]').attr('content');

            if (token) {
                xhr.setRequestHeader('X-CSRF-Token', token);
            }
        }
    });


    function createLogRow(log) {
        let eventBadge;

        switch (log.event) {
            case 'LOGIN_SUCCESS':
                eventBadge = `
                    <span class="badge bg-success-subtle text-success border border-success-subtle rounded-pill">
                        <i class="bi bi-check-circle-fill me-1"></i>SUCCESS
                    </span>`;
                break;
            case 'LOGIN_FAIL':
                eventBadge = `
                    <span class="badge bg-danger-subtle text-danger border border-danger-subtle rounded-pill">
                        <i class="bi bi-x-circle-fill me-1"></i>FAIL
                    </span>`;
                break;
            case 'ACCOUNT_CREATED':
                eventBadge = `
                    <span class="badge bg-primary-subtle text-primary border border-primary-subtle rounded-pill">
                        <i class="bi bi-person-plus-fill me-1"></i>NEW USER
                    </span>`;
                break;
            case 'ACCOUNT_DISABLED':
                eventBadge = `
                    <span class="badge bg-warning-subtle text-warning-emphasis border border-warning-subtle rounded-pill">
                        <i class="bi bi-slash-circle-fill me-1"></i>DISABLED
                    </span>`;
                break;
            case 'ACCOUNT_ENABLED':
                eventBadge = `
                    <span class="badge bg-info-subtle text-info-emphasis border border-info-subtle rounded-pill">
                        <i class="bi bi-check-circle-fill me-1"></i>ENABLED
                    </span>`;
                break;
            case 'PASSWORD_CHANGE':
                eventBadge = `
                    <span class="badge text-white rounded-pill" style="background-color: #6610f2;">
                        <i class="bi bi-key-fill me-1"></i>PWD CHANGE
                    </span>`;
                break;
            case 'ACCOUNT_DELETED':
                eventBadge = `
                    <span class="badge bg-dark text-danger border border-danger rounded-pill">
                        <i class="bi bi-trash-fill me-1"></i>DELETED
                    </span>`;
                break;
            case 'PAGE_VIEW':
                eventBadge = `
                    <span class="badge bg-secondary-subtle text-secondary border border-secondary-subtle rounded-pill">
                        VIEW
                    </span>`;
                break;
            default:
                eventBadge = `<span class="badge bg-light text-dark border rounded-pill">${escapeHtml(log.event)}</span>`;
                break;
        }

        return `
            <tr>
                <td class="text-muted small text-monospace" data-label="Timestamp">${escapeHtml(log.timestamp)}</td>
                <td class="text-monospace" data-label="IP Address">${escapeHtml(log.ip)}</td>
                <td class="fw-bold" data-label="Username">${escapeHtml(log.username)}</td>
                <td data-label="Event">${eventBadge}</td>
                <td class="small text-muted details-cell" data-label="Details">${escapeHtml(log.details)}</td>
            </tr>
        `;
    }


    function renderPagination(data) {
        const { currentPage, totalPages, filterParams } = data;
        const paginationUl = $('#paginationContainer');
        paginationUl.empty();

        if (totalPages <= 1) return;

        const sideWidth = 10;
        let pagesToShow = [];


        for (let i = 1; i <= totalPages; i++) {

            if (i === 1 || i === totalPages || (i >= currentPage - sideWidth && i <= currentPage + sideWidth)) {
                pagesToShow.push(i);
            }
        }

        pagesToShow = [...new Set(pagesToShow)];

        paginationUl.append(`
            <li class="page-item ${currentPage === 1 ? 'disabled' : ''}">
                <a class="page-link" href="?page=${currentPage - 1}&${filterParams}" data-page="${currentPage - 1}">Previous</a>
            </li>
        `);


        let lastPage = 0;
        for (const page of pagesToShow) {

            if (lastPage !== 0 && page > lastPage + 1) {
                paginationUl.append('<li class="page-item disabled"><span class="page-link">...</span></li>');
            }


            paginationUl.append(`
                <li class="page-item ${page === currentPage ? 'active' : ''}">
                    <a class="page-link" href="?page=${page}&${filterParams}" data-page="${page}">${page}</a>
                </li>
            `);
            lastPage = page;
        }


        paginationUl.append(`
            <li class="page-item ${currentPage === totalPages ? 'disabled' : ''}">
                <a class="page-link" href="?page=${currentPage + 1}&${filterParams}" data-page="${currentPage + 1}">Next</a>
            </li>
        `);
    }


    function loadLogs(page = 1, filters = '') {
        const loadingIndicator = $('#loadingIndicator');
        const tableBody = $('#logsTableBody');

        loadingIndicator.css('display', 'flex');

        const params = `action=load_logs&page=${page}&${filters}`;

        $.ajax({
            url: 'admin-logs',
            type: 'GET',
            data: params,
            dataType: 'json',
            success: function(response) {
                tableBody.empty();
                if (response.logs && response.logs.length > 0) {
                    response.logs.forEach(log => {
                        tableBody.append(createLogRow(log));
                    });
                } else {
                    // Mensaje cuando está vacío
                    tableBody.append(`
                        <tr>
                            <td colspan="7" class="text-center text-muted p-5">
                                <i class="fas fa-search fa-2x mb-3 opacity-50"></i>
                                <p class="mb-0">No logs found matching your criteria.</p>
                            </td>
                        </tr>
                    `);
                }

                renderPagination(response);
                // Actualizar URL sin recargar
                history.pushState(null, '', `admin-logs?${params.replace('action=load_logs&', '')}`);
            },
            error: (xhr) => {
                console.error("Error loading logs.");
                let msg = "Error loading data.";
                if (xhr.status === 403) msg = "Session expired. Please refresh the page.";

                tableBody.html(`
                    <tr>
                        <td colspan="7" class="text-center text-danger p-5">
                            <i class="fas fa-exclamation-triangle me-2"></i> ${msg}
                        </td>
                    </tr>
                `);
            },
            complete: () => {
                // Ocultar Overlay al terminar
                loadingIndicator.hide();
            }
        });
    }


    $('#paginationContainer').on('click', 'a.page-link', function(e) {
        e.preventDefault();
        if ($(this).parent().hasClass('disabled') || $(this).parent().hasClass('active')) {
            return;
        }
        const page = $(this).data('page');
        const filters = $('#filterForm').serialize();
        loadLogs(page, filters);
    });


    $('#filterForm').on('submit', function(e) {
        e.preventDefault();
        const filters = $(this).serialize();
        loadLogs(1, filters);
    });

    function escapeHtml(unsafe) {
        if (unsafe === null || unsafe === undefined) return "";
        return unsafe.toString().replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");
    }

    const initialParams = new URLSearchParams(window.location.search);
    const initialPage = initialParams.get('page') || 1;
    const initialFilters = $('#filterForm').serialize();
    loadLogs(initialPage, initialFilters);
});