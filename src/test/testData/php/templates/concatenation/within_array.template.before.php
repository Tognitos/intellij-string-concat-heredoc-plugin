<?php
$givenCls = new stdClass();
$givenCls->value = 'value of stdClass';
$arr = [
    'entry1',
    5 . 'a' . true . null<caret>. "{$givenCls->value}",
    'entry3'
];
