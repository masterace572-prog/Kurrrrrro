<?php if (session()->getFlashdata('msgDanger')) : ?>
    <div class="animate-slide-down mb-4 bg-gradient-to-r from-red-50 to-pink-50 border-l-4 border-red-500 rounded-lg p-4 shadow-lg">
        <div class="flex items-center">
            <div class="flex-shrink-0">
                <svg class="w-6 h-6 text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                </svg>
            </div>
            <div class="ml-3 flex-1">
                <p class="text-sm font-medium text-red-800">
                    <?= session()->getFlashdata('msgDanger') ?>
                </p>
            </div>
            <button onclick="this.parentElement.parentElement.remove()" class="flex-shrink-0 ml-4 text-red-500 hover:text-red-700 transition-colors">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                </svg>
            </button>
        </div>
    </div>
<?php elseif (session()->getFlashdata('msgSuccess')) : ?>
    <div class="animate-slide-down mb-4 bg-gradient-to-r from-green-50 to-teal-50 border-l-4 border-green-500 rounded-lg p-4 shadow-lg">
        <div class="flex items-center">
            <div class="flex-shrink-0">
                <svg class="w-6 h-6 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                </svg>
            </div>
            <div class="ml-3 flex-1">
                <p class="text-sm font-medium text-green-800">
                    <?= session()->getFlashdata('msgSuccess') ?>
                </p>
            </div>
            <button onclick="this.parentElement.parentElement.remove()" class="flex-shrink-0 ml-4 text-green-500 hover:text-green-700 transition-colors">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                </svg>
            </button>
        </div>
    </div>
<?php elseif (session()->getFlashdata('msgWarning')) : ?>
    <div class="animate-slide-down mb-4 bg-gradient-to-r from-yellow-50 to-orange-50 border-l-4 border-yellow-500 rounded-lg p-4 shadow-lg">
        <div class="flex items-center">
            <div class="flex-shrink-0">
                <svg class="w-6 h-6 text-yellow-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path>
                </svg>
            </div>
            <div class="ml-3 flex-1">
                <p class="text-sm font-medium text-yellow-800">
                    <?= session()->getFlashdata('msgWarning') ?>
                </p>
            </div>
            <button onclick="this.parentElement.parentElement.remove()" class="flex-shrink-0 ml-4 text-yellow-500 hover:text-yellow-700 transition-colors">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                </svg>
            </button>
        </div>
    </div>
<?php else : ?>
    <?php if (session()->has('userid')) : ?>
        <?php if (isset($messages)) : ?>
            <div class="animate-slide-down mb-4 bg-gradient-to-r from-<?= $messages[1] == 'success' ? 'green' : ($messages[1] == 'danger' ? 'red' : ($messages[1] == 'warning' ? 'yellow' : 'blue')) ?>-50 to-<?= $messages[1] == 'success' ? 'teal' : ($messages[1] == 'danger' ? 'pink' : ($messages[1] == 'warning' ? 'orange' : 'indigo')) ?>-50 border-l-4 border-<?= $messages[1] == 'success' ? 'green' : ($messages[1] == 'danger' ? 'red' : ($messages[1] == 'warning' ? 'yellow' : 'blue')) ?>-500 rounded-lg p-4 shadow-lg">
                <div class="flex items-center">
                    <div class="flex-shrink-0">
                        <?php if ($messages[1] == 'success') : ?>
                            <svg class="w-6 h-6 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                            </svg>
                        <?php elseif ($messages[1] == 'danger') : ?>
                            <svg class="w-6 h-6 text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                            </svg>
                        <?php elseif ($messages[1] == 'warning') : ?>
                            <svg class="w-6 h-6 text-yellow-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path>
                            </svg>
                        <?php else : ?>
                            <svg class="w-6 h-6 text-blue-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                            </svg>
                        <?php endif; ?>
                    </div>
                    <div class="ml-3 flex-1">
                        <p class="text-sm font-medium text-<?= $messages[1] == 'success' ? 'green' : ($messages[1] == 'danger' ? 'red' : ($messages[1] == 'warning' ? 'yellow' : 'blue')) ?>-800">
                            <?= $messages[0] ?>
                        </p>
                    </div>
                    <button onclick="this.parentElement.parentElement.remove()" class="flex-shrink-0 ml-4 text-<?= $messages[1] == 'success' ? 'green' : ($messages[1] == 'danger' ? 'red' : ($messages[1] == 'warning' ? 'yellow' : 'blue')) ?>-500 hover:text-<?= $messages[1] == 'success' ? 'green' : ($messages[1] == 'danger' ? 'red' : ($messages[1] == 'warning' ? 'yellow' : 'blue')) ?>-700 transition-colors">
                        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                        </svg>
                    </button>
                </div>
            </div>
        <?php else : ?>
            <div class="animate-slide-down mb-4 bg-gradient-to-r from-purple-50 to-pink-50 border-l-4 border-purple-500 rounded-lg p-4 shadow-lg">
                <div class="flex items-center">
                    <div class="flex-shrink-0">
                        <svg class="w-6 h-6 text-purple-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14 10h4.764a2 2 0 011.789 2.894l-3.5 7A2 2 0 0115.263 21h-4.017c-.163 0-.326-.02-.485-.06L7 20m7-10V5a2 2 0 00-2-2h-.095c-.5 0-.905.405-.905.905 0 .714-.211 1.412-.608 2.006L7 11v9m7-10h-2M7 20H5a2 2 0 01-2-2v-6a2 2 0 012-2h2.5"></path>
                        </svg>
                    </div>
                    <div class="ml-3 flex-1">
                        <p class="text-sm font-medium text-purple-800">
                            Welcome <span class="font-bold"><?= getName($user) ?></span>! 👋
                        </p>
                    </div>
                    <button onclick="this.parentElement.parentElement.remove()" class="flex-shrink-0 ml-4 text-purple-500 hover:text-purple-700 transition-colors">
                        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                        </svg>
                    </button>
                </div>
            </div>
        <?php endif; ?>
    <?php else : ?>
        <div class="animate-slide-down mb-4 bg-gradient-to-r from-blue-50 to-indigo-50 border-l-4 border-blue-500 rounded-lg p-4 shadow-lg">
            <div class="flex items-center">
                <div class="flex-shrink-0">
                    <svg class="w-6 h-6 text-blue-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"></path>
                    </svg>
                </div>
                <div class="ml-3 flex-1">
                    <p class="text-sm font-medium text-blue-800">
                        Welcome Stranger! 🌟 Please login to continue.
                    </p>
                </div>
                <button onclick="this.parentElement.parentElement.remove()" class="flex-shrink-0 ml-4 text-blue-500 hover:text-blue-700 transition-colors">
                    <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                    </svg>
                </button>
            </div>
        </div>
    <?php endif; ?>
<?php endif; ?>

<style>
    @keyframes slideDown {
        from {
            opacity: 0;
            transform: translateY(-20px);
        }
        to {
            opacity: 1;
            transform: translateY(0);
        }
    }
    
    .animate-slide-down {
        animation: slideDown 0.4s ease-out forwards;
    }
</style>