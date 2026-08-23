document.addEventListener("DOMContentLoaded", () => {
    let lineChart, doughnutChart;

    const updateChartTheme = () => {
        const isDark = document.documentElement.getAttribute('data-bs-theme') === 'dark';
        const textColor = isDark ? '#e0e0e0' : '#666666';
        const gridColor = isDark ? 'rgba(255, 255, 255, 0.1)' : 'rgba(0, 0, 0, 0.05)';

        [lineChart, doughnutChart].forEach(chart => {
            if (!chart) return;
            chart.options.color = textColor;
            if (chart.options.scales) {
                Object.values(chart.options.scales).forEach(scale => {
                    if (scale.grid) scale.grid.color = gridColor;
                    if (scale.ticks) scale.ticks.color = textColor;
                });
            }
            if (chart.options.plugins && chart.options.plugins.legend) {
                chart.options.plugins.legend.labels.color = textColor;
            }
            chart.update();
        });
    };

    if (document.getElementById('trafficLineChart') && typeof securityData !== 'undefined') {
        lineChart = new Chart(document.getElementById('trafficLineChart'), {
            type: 'line',
            data: {
                labels: securityData.chartLabels || [],
                datasets: [
                    {
                        label: 'Legitimate Traffic',
                        data: securityData.chartTraffic || [],
                        borderColor: '#1cc88a',
                        backgroundColor: 'rgba(28, 200, 138, 0.05)',
                        borderWidth: 2,
                        tension: 0.3,
                        fill: true
                    },
                    {
                        label: 'Blocked Attacks',
                        data: securityData.chartBlocks || [],
                        borderColor: '#e74a3b',
                        backgroundColor: 'rgba(231, 74, 59, 0.05)',
                        borderWidth: 2,
                        tension: 0.3,
                        fill: true
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: { mode: 'index', intersect: false },
                plugins: { legend: { position: 'top' } },
                scales: { y: { beginAtZero: true } }
            }
        });
    }

    if (document.getElementById('threatDoughnutChart') && typeof securityData !== 'undefined' && securityData.threatLabels && securityData.threatLabels.length > 0) {
        const colorMap = {
            'SQL_INJECTION': '#e74a3b',
            'XSS_BLOCKED': '#f6c23e',
            'WAF_BLOCKED': '#f6c23e',
            'RATE_LIMITED': '#4e73df',
            'BLACKLISTED': '#5a5c69',
            'UNAUTHORIZED_ADMIN_ACCESS': '#858796',
            'ACCOUNT_ACCESS_DENIED': '#e83e8c'
        };

        const backgroundColors = securityData.threatLabels.map(label => colorMap[label] || '#36b9cc');

        doughnutChart = new Chart(document.getElementById('threatDoughnutChart'), {
            type: 'doughnut',
            data: {
                labels: securityData.threatLabels,
                datasets: [{
                    data: securityData.threatValues,
                    backgroundColor: backgroundColors,
                    hoverOffset: 4,
                    borderColor: 'transparent'
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { position: 'bottom', labels: { boxWidth: 12, font: { size: 10 } } }
                },
                cutout: '70%'
            }
        });
    }

    updateChartTheme();
    const observer = new MutationObserver(updateChartTheme);
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-bs-theme'] });

    document.body.classList.add('page-loaded');
});