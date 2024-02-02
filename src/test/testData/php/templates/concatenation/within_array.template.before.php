<?php
$givenCls = new stdClass();
$givenCls->value = 'value of stdClass';
$arr = [
    'entry1',
    5 . 'a' . true . null<caret>. "\n" . "{$givenCls->value}" . "\n End :)" . functionCall(),
    'entry3'
];
